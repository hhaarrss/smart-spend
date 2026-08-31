"""
Router for Transaction management.

Handles transaction creation, filtering, duplicate prevention, and aggregate summaries.
"""

import calendar
import hashlib
import math
import re
from datetime import date, datetime, time, timezone
from typing import Optional, List, Dict
from fastapi import APIRouter, Depends, HTTPException, status, Query, Response
from pydantic import BaseModel
from sqlalchemy import select, and_, or_, func
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models.transaction import Transaction
from models.user import User
from models.budget import BudgetLimit
from models.merchant_mapping import MerchantMapping
from schemas.transaction import (
    TransactionCreate,
    TransactionUpdate,
    TransactionResponse,
    TransactionSummaryResponse,
    PaginatedTransactionResponse,
    CategorySummaryItem,
    MonthlyCategorySummaryResponse,
    NeedsReviewResponse,
    CategorizeRequest,
    SMSRequest,
    BatchSMSRequest,
    CorrectionRequest,
)
from schemas.sms import SMSIngestionRequest, SMSIngestionResponse
from utils.sms_parser import parse_sms
from utils.dependencies import get_current_user
from utils.fingerprint import generate_fingerprint
from utils.categories import CATEGORIES
from utils.transfer_detector import detect_p2p_transfer
from utils.notifications import check_budget_and_alert
from categorizer.transaction_categorizer import (
    categorize_transaction,
    process_upi_sms,
    process_batch,
    save_user_correction,
    normalize_name,
)

router = APIRouter(prefix="/transactions", tags=["Transactions"])


def categorize_parsed_sms(parsed: dict, raw_sms: str) -> dict:
    """
    Categorize a transaction after reliable bank-SMS extraction.
    Fallback/no-confidence matches are routed to Needs Review with review_status='needs_review'.
    """
    enriched = categorize_transaction({
        **parsed,
        "raw": raw_sms,
        "merchant_raw": parsed.get("merchant"),
    }) or {}

    category = enriched.get("category") or "Needs Review"
    confidence = enriched.get("confidence") or "none"
    source = enriched.get("source") or "fallback"

    review_status = "auto_categorized"
    if source == "fallback" or confidence in ("none", "low") or category == "Miscellaneous" or category == "Needs Review":
        category = "Needs Review"
        review_status = "needs_review"

    return {
        "category": category,
        "subcategory": enriched.get("subcategory"),
        "merchant": enriched.get("merchant") or parsed.get("merchant"),
        "source": source,
        "confidence": confidence,
        "review_status": review_status,
    }


@router.post(
    "/",
    response_model=TransactionResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a new transaction",
)
async def create_transaction(
    tx_in: TransactionCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> Transaction:
    """
    Creates a new transaction for the authenticated user.
    Auto-generates a unique fingerprint and prevents double-posting.

    Args:
        tx_in (TransactionCreate): Transaction data.
        current_user (User): Authenticated user.
        db (AsyncSession): The database session.

    Raises:
        HTTPException: 409 Conflict if a duplicate transaction is detected.

    Returns:
        Transaction: The inserted Transaction database object.
    """
    fingerprint = generate_fingerprint(
        user_id=current_user.id,
        amount=tx_in.amount,
        date_val=tx_in.date,
        account_last4=tx_in.account_last4,
    )

    # Check for duplicate
    duplicate_query = select(Transaction).where(Transaction.hash_fingerprint == fingerprint)
    result = await db.execute(duplicate_query)
    if result.scalars().first():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Transaction already exists (duplicate detected by fingerprint).",
        )

    # P2P Transfer detection
    is_tx_transfer, recipient = detect_p2p_transfer(tx_in.merchant, raw_sms=None, category=tx_in.category)
    final_cat = "Transfer" if is_tx_transfer else tx_in.category

    # Instantiate model
    new_tx = Transaction(
        user_id=current_user.id,
        amount=tx_in.amount,
        type=tx_in.type.lower(),
        category=final_cat,
        merchant=tx_in.merchant,
        bank=tx_in.bank,
        account_last4=tx_in.account_last4,
        date=tx_in.date,
        hash_fingerprint=fingerprint,
        source=tx_in.source.lower(),
        is_transfer=is_tx_transfer,
        transfer_to=recipient,
        notes=tx_in.notes,
    )

    db.add(new_tx)
    await db.flush()

    if new_tx.type == "debit":
        await check_budget_and_alert(db, current_user.id, new_tx.category, float(new_tx.amount))

    return new_tx


@router.get(
    "/monthly-category-summary",
    response_model=MonthlyCategorySummaryResponse,
    summary="Compute detailed monthly category spending summary with MoM change",
)
async def get_monthly_category_summary(
    month: int = Query(..., ge=1, le=12, description="Target month (1-12)"),
    year: int = Query(..., ge=2020, le=2030, description="Target year (e.g. 2026)"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> MonthlyCategorySummaryResponse:
    """
    Computes a detailed spending breakdown by category for a specific month and year.
    Includes category percentage share, transaction count, top merchant, budget limits,
    budget utilization percent, and month-over-month change.

    Args:
        month (int): Target month (1-12).
        year (int): Target year (2020-2030).
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Returns:
        MonthlyCategorySummaryResponse: Structured monthly spending summary.
    """
    # 1. Target month range
    last_day = calendar.monthrange(year, month)[1]
    start_dt = datetime(year, month, 1, 0, 0, 0, tzinfo=timezone.utc)
    end_dt = datetime(year, month, last_day, 23, 59, 59, 999999, tzinfo=timezone.utc)

    # 2. Previous month range
    if month == 1:
        prev_month = 12
        prev_year = year - 1
    else:
        prev_month = month - 1
        prev_year = year
    prev_last_day = calendar.monthrange(prev_year, prev_month)[1]
    prev_start_dt = datetime(prev_year, prev_month, 1, 0, 0, 0, tzinfo=timezone.utc)
    prev_end_dt = datetime(prev_year, prev_month, prev_last_day, 23, 59, 59, 999999, tzinfo=timezone.utc)

    # 3. Fetch target month debits for current user
    debits_query = select(Transaction).where(
        and_(
            Transaction.user_id == current_user.id,
            Transaction.type == "debit",
            Transaction.date >= start_dt,
            Transaction.date <= end_dt,
        )
    )
    debits_res = await db.execute(debits_query)
    debits = list(debits_res.scalars().all())

    total_spent = round(sum(t.amount for t in debits), 2)
    merchant_spent = round(sum(t.amount for t in debits if not t.is_transfer), 2)
    transfer_sent = round(sum(t.amount for t in debits if t.is_transfer), 2)

    # Credits for transfer_received calculation
    credits_query = select(Transaction).where(
        and_(
            Transaction.user_id == current_user.id,
            Transaction.type == "credit",
            Transaction.date >= start_dt,
            Transaction.date <= end_dt,
            Transaction.is_transfer == True
        )
    )
    credits_res = await db.execute(credits_query)
    transfer_received = round(sum(t.amount for t in credits_res.scalars().all()), 2)

    # 4. Fetch previous month total spent
    prev_query = select(func.coalesce(func.sum(Transaction.amount), 0.0)).where(
        and_(
            Transaction.user_id == current_user.id,
            Transaction.type == "debit",
            Transaction.date >= prev_start_dt,
            Transaction.date <= prev_end_dt,
        )
    )
    prev_res = await db.execute(prev_query)
    previous_month_total = round(float(prev_res.scalar() or 0.0), 2)

    # 5. Compute Month-over-Month change percentage
    if previous_month_total > 0:
        mom_change = round(((total_spent - previous_month_total) / previous_month_total) * 100, 1)
    else:
        mom_change = 0.0

    # 6. Fetch user's budget limits from database
    budgets_query = select(BudgetLimit).where(BudgetLimit.user_id == current_user.id)
    budgets_res = await db.execute(budgets_query)
    budget_map = {b.category.lower(): float(b.monthly_limit) for b in budgets_res.scalars().all()}

    # 7. Group target debits by category
    category_groups: Dict[str, List[Transaction]] = {}
    for t in debits:
        cat_name = t.category.strip() if t.category else "Other"
        category_groups.setdefault(cat_name, []).append(t)

    categories_summary = []
    for cat_name, cat_txs in category_groups.items():
        cat_total = round(sum(t.amount for t in cat_txs), 2)
        percentage = round((cat_total / total_spent) * 100, 1) if total_spent > 0 else 0.0
        tx_count = len(cat_txs)

        # Determine top merchant in this category
        merchant_totals: Dict[str, float] = {}
        for t in cat_txs:
            if t.merchant and t.merchant.strip():
                m_name = t.merchant.strip()
                merchant_totals[m_name] = merchant_totals.get(m_name, 0.0) + t.amount

        if merchant_totals:
            top_merchant = max(merchant_totals.items(), key=lambda x: x[1])[0]
        else:
            top_merchant = "N/A"

        b_limit = budget_map.get(cat_name.lower(), 0.0)
        b_used_pct = round((cat_total / b_limit) * 100, 1) if b_limit > 0 else 0.0

        categories_summary.append(
            CategorySummaryItem(
                category=cat_name,
                total=cat_total,
                percentage=percentage,
                transaction_count=tx_count,
                top_merchant=top_merchant,
                budget_limit=b_limit,
                budget_used_percent=b_used_pct,
            )
        )

    # Sort categories by total DESC
    categories_summary.sort(key=lambda x: x.total, reverse=True)

    return MonthlyCategorySummaryResponse(
        month=month,
        year=year,
        total_spent=total_spent,
        merchant_spent=merchant_spent,
        transfer_sent=transfer_sent,
        transfer_received=transfer_received,
        categories=categories_summary,
        previous_month_total=previous_month_total,
        month_over_month_change=mom_change,
    )


@router.get(
    "/",
    response_model=PaginatedTransactionResponse,
    summary="Retrieve paginated and filtered transactions sorted latest first",
)
async def list_transactions(
    page: int = Query(1, ge=1, description="Page number (default 1)"),
    limit: int = Query(10, ge=1, le=50, description="Items per page (default 10, max 50)"),
    month: Optional[int] = Query(None, ge=1, le=12, description="Filter by month (1-12)"),
    year: Optional[int] = Query(None, ge=2020, le=2030, description="Filter by year (e.g. 2026)"),
    start_date: Optional[date] = Query(None, description="Start date filter (YYYY-MM-DD)"),
    end_date: Optional[date] = Query(None, description="End date filter (YYYY-MM-DD)"),
    category: Optional[str] = Query(None, description="Filter transactions by category"),
    type: Optional[str] = Query(None, description="Filter by transaction type ('debit' or 'credit')"),
    review_status: Optional[str] = Query(None, description="Filter by review status ('needs_review', 'reviewed', 'auto_categorized')"),
    include_transfers: bool = Query(True, description="If false, excludes P2P transfer transactions."),
    user_id: Optional[int] = Query(None, description="Filter by user ID (admin/family group access only)"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> PaginatedTransactionResponse:
    """
    Lists and filters transactions belonging to the authenticated user.
    Supports pagination, date range filtering, month/year filtering, and sorting latest first.

    Args:
        page (int): Current page number (1-indexed).
        limit (int): Max items per page (1-50).
        month (Optional[int]): Month filter (1-12).
        year (Optional[int]): Year filter (2020-2030).
        start_date (Optional[date]): Start date filter.
        end_date (Optional[date]): End date filter.
        category (Optional[str]): Category filter.
        type (Optional[str]): Filter by type ('debit' or 'credit').
        review_status (Optional[str]): Review status filter.
        include_transfers (bool): Include or exclude P2P transfers.
        user_id (Optional[int]): Query for another user's transactions (family group only).
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Raises:
        HTTPException: 403 Forbidden if trying to access data of a user outside their family group.

    Returns:
        PaginatedTransactionResponse: Paginated transaction payload with metadata.
    """
    query_target_user_id = current_user.id

    if user_id and user_id != current_user.id:
        if not current_user.family_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You must be part of a family group to query other users' transactions."
            )
        member_check = select(User).where(and_(User.id == user_id, User.family_id == current_user.family_id))
        res = await db.execute(member_check)
        if not res.scalars().first():
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You can only view transactions of members in your own family group."
            )
        query_target_user_id = user_id

    conditions = [Transaction.user_id == query_target_user_id]

    if not include_transfers:
        conditions.append(Transaction.is_transfer == False)

    # Date range & Month/Year filters
    if month is not None and year is not None:
        last_day = calendar.monthrange(year, month)[1]
        start_of_month = datetime(year, month, 1, 0, 0, 0, tzinfo=timezone.utc)
        end_of_month = datetime(year, month, last_day, 23, 59, 59, 999999, tzinfo=timezone.utc)
        conditions.append(Transaction.date >= start_of_month)
        conditions.append(Transaction.date <= end_of_month)
    else:
        if start_date:
            start_dt = datetime.combine(start_date, time.min).replace(tzinfo=timezone.utc)
            conditions.append(Transaction.date >= start_dt)
        if end_date:
            end_dt = datetime.combine(end_date, time.max).replace(tzinfo=timezone.utc)
            conditions.append(Transaction.date <= end_dt)

    if category:
        conditions.append(Transaction.category.ilike(category))
    if type:
        conditions.append(Transaction.type == type.lower())
    if review_status:
        conditions.append(Transaction.review_status.ilike(review_status))

    # Total matching count query
    count_query = select(func.count()).select_from(Transaction).where(and_(*conditions))
    count_res = await db.execute(count_query)
    total_count = count_res.scalar() or 0

    total_pages = math.ceil(total_count / limit) if total_count > 0 else 0
    has_more = page < total_pages
    offset = (page - 1) * limit

    # Items query sorted latest first (ORDER BY date DESC, created_at DESC)
    items_query = (
        select(Transaction)
        .where(and_(*conditions))
        .order_by(Transaction.date.desc(), Transaction.created_at.desc())
        .offset(offset)
        .limit(limit)
    )
    items_res = await db.execute(items_query)
    transactions = list(items_res.scalars().all())

    return PaginatedTransactionResponse(
        transactions=transactions,
        total_count=total_count,
        page=page,
        limit=limit,
        has_more=has_more,
        total_pages=total_pages,
    )


@router.get(
    "/summary",
    response_model=Dict[str, float],
    summary="Get category totals for a given month",
)
async def get_summary(
    month: str = Query(..., description="Given month in YYYY-MM format, e.g., 2026-05"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> Dict[str, float]:
    """
    Computes category totals for the authenticated user for a given month.

    Args:
        month (str): The month to filter by, formatted as YYYY-MM.
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Raises:
        HTTPException: 400 Bad Request if the month format is invalid.

    Returns:
        Dict[str, float]: Aggregated transaction totals grouped by category.
    """
    try:
        parsed_month = datetime.strptime(month, "%Y-%m")
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid month format. Use YYYY-MM format, e.g., 2026-05."
        )

    _, last_day = calendar.monthrange(parsed_month.year, parsed_month.month)
    start_date = datetime(parsed_month.year, parsed_month.month, 1, 0, 0, 0, tzinfo=timezone.utc)
    end_date = datetime(parsed_month.year, parsed_month.month, last_day, 23, 59, 59, 999999, tzinfo=timezone.utc)

    query = (
        select(Transaction.category, func.sum(Transaction.amount))
        .where(
            and_(
                Transaction.user_id == current_user.id,
                Transaction.date >= start_date,
                Transaction.date <= end_date
            )
        )
        .group_by(Transaction.category)
    )
    result = await db.execute(query)

    return {row[0]: float(row[1]) for row in result.all()}


@router.post(
    "/ingest-sms",
    response_model=SMSIngestionResponse,
    status_code=status.HTTP_200_OK,
    summary="Ingest a transaction via raw SMS",
)
async def ingest_sms(
    sms_in: SMSIngestionRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> SMSIngestionResponse:
    """
    Parses and processes an incoming SMS transaction alert.
    Checks for duplicates using SHA-256 hash of (amount + date + account_last4).

    Args:
        sms_in (SMSIngestionRequest): Inbound raw SMS message and sender.
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Returns:
        SMSIngestionResponse: Ingestion response with success, transaction object, and status message.
    """
    parsed = parse_sms(sms_in.raw_sms, sms_in.sender)
    if not parsed:
        return SMSIngestionResponse(
            success=False,
            transaction=None,
            message="Not a bank transaction SMS"
        )

    # Categorize the merchant using categorization engine
    cat_info = categorize_parsed_sms(parsed, sms_in.raw_sms)
    category = cat_info["category"]
    merchant = cat_info["merchant"] or parsed["merchant"]
    subcategory = cat_info.get("subcategory")
    source = cat_info.get("source") or "sms"
    confidence = cat_info.get("confidence") or "medium"
    review_status = cat_info.get("review_status") or "auto_categorized"

    # Generate the duplicate checker fingerprint
    fingerprint = generate_fingerprint(
        user_id=current_user.id,
        amount=parsed["amount"],
        date_val=parsed["date"],
        account_last4=parsed["account_last4"],
    )

    # Check for duplicate
    duplicate_query = select(Transaction).where(Transaction.hash_fingerprint == fingerprint)
    result = await db.execute(duplicate_query)
    if result.scalars().first():
        return SMSIngestionResponse(
            success=False,
            transaction=None,
            message="Duplicate transaction detected"
        )

    # Convert date to timezone-aware UTC if timezone-naive
    tx_date = parsed["date"]
    if tx_date.tzinfo is None:
        tx_date = tx_date.replace(tzinfo=timezone.utc)

    # P2P Transfer detection
    is_tx_transfer, recipient = detect_p2p_transfer(merchant, raw_sms=sms_in.raw_sms, category=category)
    if is_tx_transfer:
        category = "Transfer"

    # Save transaction to database
    new_tx = Transaction(
        user_id=current_user.id,
        amount=parsed["amount"],
        type=parsed["type"],
        category=category,
        subcategory=subcategory,
        merchant=merchant,
        raw_sms=sms_in.raw_sms,
        bank=parsed["bank"],
        account_last4=parsed["account_last4"],
        date=tx_date,
        hash_fingerprint=fingerprint,
        source=source,
        confidence=confidence,
        review_status=review_status,
        is_transfer=is_tx_transfer,
        transfer_to=recipient,
    )

    db.add(new_tx)
    await db.flush()

    if new_tx.type == "debit":
        await check_budget_and_alert(db, current_user.id, new_tx.category, float(new_tx.amount))

    return SMSIngestionResponse(
        success=True,
        transaction=new_tx,
        message="SMS ingested successfully"
    )


@router.post(
    "/parse-sms",
    response_model=TransactionResponse,
    status_code=status.HTTP_201_CREATED,
    summary="[Deprecated] Parse and save a single UPI transaction SMS (use /ingest-sms instead)",
    deprecated=True,
)
async def parse_and_save_sms(
    body: SMSRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Transaction:
    """
    [DEPRECATED] Delegates to the canonical /transactions/ingest-sms endpoint.
    Use POST /transactions/ingest-sms for all new integrations.

    Args:
        body (SMSRequest): Inbound raw SMS message text.
        db (AsyncSession): The database session.
        current_user (User): Authenticated user.

    Raises:
        HTTPException: 422 Unprocessable Entity if parsing fails or 409 if duplicate.

    Returns:
        Transaction: The saved or existing Transaction object.
    """
    response = await ingest_sms(
        sms_in=SMSIngestionRequest(raw_sms=body.sms_text, sender="UNKNOWN"),
        current_user=current_user,
        db=db,
    )
    if not response.success or not response.transaction:
        if "duplicate" in response.message.lower():
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=response.message
            )
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=response.message
        )
    return response.transaction


@router.post(
    "/parse-sms/batch",
    status_code=status.HTTP_201_CREATED,
    summary="Parse and save a batch of historical transaction SMS",
)
async def parse_and_save_batch(
    body: BatchSMSRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> dict:
    """
    Processes multiple SMS messages at once, typically on mobile application first launch.
    Automatically filters duplicates and records successful creations.

    Args:
        body (BatchSMSRequest): List of raw SMS strings.
        db (AsyncSession): The database session.
        current_user (User): Authenticated user.

    Returns:
        dict: Summary of parsed, saved, and skipped items.
    """
    results = process_batch(body.sms_list)
    saved = 0

    for result in results:
        if not result or result.get("amount") is None:
            continue

        tx_date = datetime.now(timezone.utc)
        if result.get("date"):
            for fmt in ("%d-%m-%y", "%d-%m-%Y", "%d/%m/%y", "%d/%m/%Y", "%d%b%y", "%d %b %y", "%d %b %Y"):
                try:
                    cleaned_date = re.sub(r"\s+", " ", result["date"].strip())
                    parsed_dt = datetime.strptime(cleaned_date, fmt)
                    tx_date = parsed_dt.replace(tzinfo=timezone.utc)
                    break
                except ValueError:
                    continue

        fingerprint = generate_fingerprint(
            user_id=current_user.id,
            amount=result["amount"],
            date_val=tx_date,
            account_last4=result.get("account_last4"),
        )

        duplicate_query = select(Transaction).where(Transaction.hash_fingerprint == fingerprint)
        dup_res = await db.execute(duplicate_query)
        if dup_res.scalars().first():
            continue

        transaction = Transaction(
            user_id=current_user.id,
            amount=result["amount"],
            merchant=result.get("merchant"),
            category=result.get("category") or "Miscellaneous",
            subcategory=result.get("subcategory"),
            type=result.get("type") or "debit",
            upi_ref=result.get("upi_ref"),
            raw_sms=result.get("raw"),
            source=result.get("source") or "sms",
            confidence=result.get("confidence") or "none",
            date=tx_date,
            hash_fingerprint=fingerprint,
            bank=result.get("bank"),
        )
        db.add(transaction)
        saved += 1

    if saved > 0:
        await db.commit()

    return {
        "total_received": len(body.sms_list),
        "total_saved": saved,
        "skipped": len(body.sms_list) - saved,
    }


@router.patch(
    "/{transaction_id}/recategorize",
    status_code=status.HTTP_200_OK,
    summary="Update transaction category and record user feedback correction",
)
async def recategorize_transaction(
    transaction_id: int,
    body: CorrectionRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> dict:
    """
    Allows users to manually correct a transaction's category.
    Updates the record in database and registers feedback correction for future auto-matching.

    Args:
        transaction_id (int): ID of the transaction to correct.
        body (CorrectionRequest): Re-categorization details.
        db (AsyncSession): The database session.
        current_user (User): Authenticated user.

    Raises:
        HTTPException: 404 Not Found if transaction doesn't exist or doesn't belong to current user.

    Returns:
        dict: Success confirmation payload.
    """
    transaction_query = select(Transaction).where(
        and_(
            Transaction.id == transaction_id,
            Transaction.user_id == current_user.id
        )
    )
    res = await db.execute(transaction_query)
    transaction = res.scalars().first()

    if not transaction:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Transaction not found"
        )

    transaction.category = body.new_category
    transaction.subcategory = body.subcategory
    transaction.source = "user_correction"
    transaction.confidence = "high"
    transaction.review_status = "reviewed"

    # Save or update MerchantMapping in database
    merchant_key = normalize_name(body.merchant_raw)
    mapping_query = select(MerchantMapping).where(
        and_(
            MerchantMapping.user_id == current_user.id,
            MerchantMapping.merchant_key == merchant_key
        )
    )
    mapping_res = await db.execute(mapping_query)
    existing_mapping = mapping_res.scalars().first()

    if existing_mapping:
        existing_mapping.category = body.new_category
        existing_mapping.subcategory = body.subcategory
        existing_mapping.display_name = body.display_name or body.merchant_raw
        existing_mapping.count += 1
    else:
        new_mapping = MerchantMapping(
            user_id=current_user.id,
            merchant_key=merchant_key,
            category=body.new_category,
            subcategory=body.subcategory,
            display_name=body.display_name or body.merchant_raw,
            count=1,
        )
        db.add(new_mapping)

    await db.commit()

    # Save correction synchronously (local file writes)
    save_user_correction(
        merchant_raw=body.merchant_raw,
        new_category=body.new_category,
        subcategory=body.subcategory,
        display_name=body.display_name,
    )

    return {
        "transaction_id": transaction_id,
        "category": body.new_category,
        "subcategory": body.subcategory,
        "review_status": "reviewed",
        "message": "Category updated and correction saved ✅",
    }


class TransactionUpdateSchema(BaseModel):
    category: Optional[str] = None
    subcategory: Optional[str] = None
    review_status: Optional[str] = None
    merchant: Optional[str] = None


@router.patch(
    "/items/{transaction_id}",
    response_model=TransactionResponse,
    summary="Update transaction fields (category, review_status, merchant)",
)
@router.patch(
    "/{transaction_id}",
    response_model=TransactionResponse,
    summary="Update transaction fields (category, review_status, merchant)",
)
async def update_transaction_fields(
    transaction_id: int,
    body: TransactionUpdateSchema,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Transaction:
    """
    Updates transaction category, review_status, or merchant fields.
    Also trains the categorizer engine with user correction feedback.
    """
    transaction_query = select(Transaction).where(
        and_(
            Transaction.id == transaction_id,
            Transaction.user_id == current_user.id
        )
    )
    res = await db.execute(transaction_query)
    transaction = res.scalars().first()

    if not transaction:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Transaction not found"
        )

    if body.category is not None:
        transaction.category = body.category
        transaction.review_status = body.review_status or "reviewed"
        transaction.source = "user_correction"
        transaction.confidence = "high"

        # Save user learning correction
        merchant_name = transaction.merchant or "Unknown Merchant"
        try:
            save_user_correction(
                merchant_raw=merchant_name,
                new_category=body.category,
                subcategory=body.subcategory,
                display_name=merchant_name,
            )
        except Exception as e:
            pass

    if body.subcategory is not None:
        transaction.subcategory = body.subcategory
    if body.review_status is not None:
        transaction.review_status = body.review_status
    if body.merchant is not None:
        transaction.merchant = body.merchant

    await db.commit()
    await db.refresh(transaction)
    return transaction


@router.patch("/{transaction_id}", response_model=TransactionResponse, summary="Edit a transaction")
async def edit_transaction(
    transaction_id: int,
    updates: TransactionUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Transaction:
    """
    Partially edit an existing transaction owned by the current user.
    Editable fields: category, merchant, amount, date, notes.
    """
    res = await db.execute(select(Transaction).where(Transaction.id == transaction_id))
    transaction = res.scalars().first()

    if not transaction:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Transaction not found"
        )

    if transaction.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not authorized to edit this transaction"
        )

    if updates.category is not None:
        transaction.category = updates.category
    if updates.merchant is not None:
        transaction.merchant = updates.merchant
    if updates.amount is not None:
        transaction.amount = updates.amount
    if updates.date is not None:
        transaction.date = updates.date
    if updates.notes is not None:
        transaction.notes = updates.notes

    await db.commit()
    await db.refresh(transaction)
    return transaction


@router.delete("/{transaction_id}", summary="Delete a transaction")
async def delete_transaction(
    transaction_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> dict:
    """
    Hard delete a transaction owned by the current user.
    """
    res = await db.execute(select(Transaction).where(Transaction.id == transaction_id))
    transaction = res.scalars().first()

    if not transaction:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Transaction not found"
        )

    if transaction.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not authorized to delete this transaction"
        )

    await db.delete(transaction)
    await db.commit()
    return {"message": "Transaction deleted successfully"}


@router.get(
    "/needs-review",
    response_model=NeedsReviewResponse,
    summary="Get unreviewed transactions needing user categorization"
)
async def get_needs_review_transactions(
    response: Response,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Fetch all transactions for current user where category is 'Other'
    or review_status is 'needs_review'. Returns count in X-Needs-Review-Count header.
    """
    query = select(Transaction).where(
        and_(
            Transaction.user_id == current_user.id,
            or_(
                func.lower(Transaction.category) == "other",
                Transaction.review_status == "needs_review"
            )
        )
    ).order_by(Transaction.created_at.desc())

    result = await db.execute(query)
    unreviewed = result.scalars().all()
    count = len(unreviewed)

    response.headers["X-Needs-Review-Count"] = str(count)

    return NeedsReviewResponse(
        count=count,
        transactions=unreviewed,
        message=f"Fix these {count} transactions to improve accuracy" if count > 0 else "All done for today! 🎉"
    )


@router.patch(
    "/{transaction_id}/categorize",
    summary="1-click categorize transaction & learn merchant mapping"
)
@router.patch(
    "/{transaction_id}/recategorize",
    summary="Re-categorize transaction alias endpoint"
)
async def categorize_transaction_item(
    transaction_id: int,
    payload: CategorizeRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Updates transaction category, marks review_status as 'reviewed',
    and optionally saves merchant_alias mapping to merchant_mappings table for automatic learning.
    """
    res = await db.execute(select(Transaction).where(Transaction.id == transaction_id))
    transaction = res.scalars().first()

    if not transaction:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Transaction not found"
        )

    if transaction.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not authorized to edit this transaction"
        )

    req_category = payload.target_category
    # Validate category against canonical list
    cat_match = next((c for c in CATEGORIES if c.lower() == req_category.lower()), None)
    if not cat_match:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid category '{req_category}'. Must be one of {CATEGORIES}"
        )

    transaction.category = cat_match
    transaction.review_status = "reviewed"

    learned = False
    target_alias = (payload.target_merchant or transaction.merchant or "").strip()
    if target_alias:
        # Check existing mapping
        m_query = select(MerchantMapping).where(
            and_(
                MerchantMapping.user_id == current_user.id,
                func.lower(MerchantMapping.merchant_key) == target_alias.lower()
            )
        )
        m_res = await db.execute(m_query)
        existing_map = m_res.scalars().first()

        if existing_map:
            existing_map.category = cat_match
            existing_map.count += 1
        else:
            new_map = MerchantMapping(
                user_id=current_user.id,
                merchant_key=target_alias.lower(),
                category=cat_match,
                display_name=target_alias.title()
            )
            db.add(new_map)
        learned = True

    await db.commit()
    await db.refresh(transaction)

    # Check budget alert threshold
    if transaction.type == "debit":
        await check_budget_and_alert(db, current_user.id, transaction.category, float(transaction.amount))

    return {"updated": True, "learned": learned, "category": cat_match}


