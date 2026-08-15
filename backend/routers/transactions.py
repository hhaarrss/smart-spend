"""
Router for Transaction management.

Handles transaction creation, filtering, duplicate prevention, and aggregate summaries.
"""

import calendar
import hashlib
import re
from datetime import datetime, timezone
"""
Router for Transaction management.

Handles transaction creation, filtering, duplicate prevention, and aggregate summaries.
"""

import calendar
import hashlib
import re
from datetime import datetime, timezone
from typing import Optional, List, Dict
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy import select, and_, func
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models.transaction import Transaction
from models.user import User
from models.merchant_mapping import MerchantMapping
from schemas.transaction import (
    TransactionCreate,
    TransactionResponse,
    TransactionSummaryResponse,
    SMSRequest,
    BatchSMSRequest,
    CorrectionRequest,
)
from schemas.sms import SMSIngestionRequest, SMSIngestionResponse
from utils.sms_parser import parse_sms
from utils.dependencies import get_current_user
from categorizer.transaction_categorizer import (
    categorize_transaction,
    process_upi_sms,
    process_batch,
    save_user_correction,
    normalize_name,
)

router = APIRouter(prefix="/transactions", tags=["Transactions"])


def generate_fingerprint(user_id: int, amount: float, tx_type: str, category: str, date: datetime) -> str:
    """
    Generates a unique SHA-256 fingerprint for a transaction to prevent duplicates.

    Args:
        user_id (int): The ID of the transaction owner.
        amount (float): The transaction amount.
        tx_type (str): Debit or Credit.
        category (str): The transaction category.
        date (datetime): The transaction date.

    Returns:
        str: Unique hexadecimal representation of the SHA-256 hash.
    """
    raw_str = f"{user_id}:{amount:.2f}:{tx_type.lower()}:{category.lower()}:{date.isoformat()}"
    return hashlib.sha256(raw_str.encode("utf-8")).hexdigest()


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
    if source == "fallback" or confidence == "none" or category == "Miscellaneous" or category == "Needs Review":
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
        tx_type=tx_in.type,
        category=tx_in.category,
        date=tx_in.date
    )

    # Check for duplicate
    duplicate_query = select(Transaction).where(Transaction.hash_fingerprint == fingerprint)
    result = await db.execute(duplicate_query)
    if result.scalars().first():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Transaction already exists (duplicate detected by fingerprint).",
        )

    # Instantiate model
    new_tx = Transaction(
        user_id=current_user.id,
        amount=tx_in.amount,
        type=tx_in.type.lower(),
        category=tx_in.category,
        merchant=tx_in.merchant,
        bank=tx_in.bank,
        account_last4=tx_in.account_last4,
        date=tx_in.date,
        hash_fingerprint=fingerprint,
        source=tx_in.source.lower(),
    )

    db.add(new_tx)
    await db.flush()
    
    return new_tx


@router.get(
    "/",
    response_model=List[TransactionResponse],
    summary="Retrieve all transactions with optional filters",
)
async def list_transactions(
    category: Optional[str] = Query(None, description="Filter transactions by category"),
    start_date: Optional[datetime] = Query(None, description="Start date for range filter"),
    end_date: Optional[datetime] = Query(None, description="End date for range filter"),
    type: Optional[str] = Query(None, description="Filter by transaction type ('debit' or 'credit')"),
    user_id: Optional[int] = Query(None, description="Filter by user ID (admin/family only)"),
    limit: int = Query(50, description="Max number of transactions to retrieve"),
    offset: int = Query(0, description="Offset for pagination"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> List[Transaction]:
    """
    Lists and filters transactions belonging to the current user.
    If a specific user_id is requested, verifies they are in the same family.

    Args:
        category (Optional[str]): Category to filter.
        start_date (Optional[datetime]): From date.
        end_date (Optional[datetime]): To date.
        type (Optional[str]): Filter by transaction type ('debit' or 'credit').
        user_id (Optional[int]): Query for another user's transactions.
        limit (int): Pagination limit.
        offset (int): Pagination offset.
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Raises:
        HTTPException: 403 Forbidden if trying to access data of a user outside their family group.

    Returns:
        List[Transaction]: List of transaction database records.
    """
    query_target_user_id = current_user.id

    # If querying another user, ensure they are family members
    if user_id and user_id != current_user.id:
        if not current_user.family_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You must be part of a family group to query other users' transactions."
            )
        
        # Verify the requested user is in the same family
        member_check = select(User).where(and_(User.id == user_id, User.family_id == current_user.family_id))
        res = await db.execute(member_check)
        if not res.scalars().first():
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You can only view transactions of members in your own family group."
            )
        query_target_user_id = user_id

    # Build conditional query
    conditions = [Transaction.user_id == query_target_user_id]

    if category:
        conditions.append(Transaction.category.ilike(category))
    if start_date:
        conditions.append(Transaction.date >= start_date)


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
        tx_type=tx_in.type,
        category=tx_in.category,
        date=tx_in.date
    )

    # Check for duplicate
    duplicate_query = select(Transaction).where(Transaction.hash_fingerprint == fingerprint)
    result = await db.execute(duplicate_query)
    if result.scalars().first():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Transaction already exists (duplicate detected by fingerprint).",
        )

    # Instantiate model
    new_tx = Transaction(
        user_id=current_user.id,
        amount=tx_in.amount,
        type=tx_in.type.lower(),
        category=tx_in.category,
        merchant=tx_in.merchant,
        bank=tx_in.bank,
        account_last4=tx_in.account_last4,
        date=tx_in.date,
        hash_fingerprint=fingerprint,
        source=tx_in.source.lower(),
    )

    db.add(new_tx)
    await db.flush()
    
    return new_tx


@router.get(
    "/",
    response_model=List[TransactionResponse],
    summary="Retrieve all transactions with optional filters",
)
async def list_transactions(
    category: Optional[str] = Query(None, description="Filter transactions by category"),
    start_date: Optional[datetime] = Query(None, description="Start date for range filter"),
    end_date: Optional[datetime] = Query(None, description="End date for range filter"),
    type: Optional[str] = Query(None, description="Filter by transaction type ('debit' or 'credit')"),
    review_status: Optional[str] = Query(None, description="Filter by review status ('needs_review', 'reviewed', 'auto_categorized')"),
    user_id: Optional[int] = Query(None, description="Filter by user ID (admin/family only)"),
    limit: int = Query(50, description="Max number of transactions to retrieve"),
    offset: int = Query(0, description="Offset for pagination"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> List[Transaction]:
    """
    Lists and filters transactions belonging to the current user.
    If a specific user_id is requested, verifies they are in the same family.

    Args:
        category (Optional[str]): Category to filter.
        start_date (Optional[datetime]): From date.
        end_date (Optional[datetime]): To date.
        type (Optional[str]): Filter by transaction type ('debit' or 'credit').
        user_id (Optional[int]): Query for another user's transactions.
        limit (int): Pagination limit.
        offset (int): Pagination offset.
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Raises:
        HTTPException: 403 Forbidden if trying to access data of a user outside their family group.

    Returns:
        List[Transaction]: List of transaction database records.
    """
    query_target_user_id = current_user.id

    # If querying another user, ensure they are family members
    if user_id and user_id != current_user.id:
        if not current_user.family_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You must be part of a family group to query other users' transactions."
            )
        
        # Verify the requested user is in the same family
        member_check = select(User).where(and_(User.id == user_id, User.family_id == current_user.family_id))
        res = await db.execute(member_check)
        if not res.scalars().first():
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You can only view transactions of members in your own family group."
            )
        query_target_user_id = user_id

    # Build conditional query
    conditions = [Transaction.user_id == query_target_user_id]

    if category:
        conditions.append(Transaction.category.ilike(category))
    if start_date:
        conditions.append(Transaction.date >= start_date)
    if end_date:
        conditions.append(Transaction.date <= end_date)
    if type:
        conditions.append(Transaction.type == type.lower())
    if review_status:
        conditions.append(Transaction.review_status.ilike(review_status))

    query = select(Transaction).where(and_(*conditions)).order_by(Transaction.date.desc())
    if offset:
        query = query.offset(offset)
    if limit:
        query = query.limit(limit)
        
    result = await db.execute(query)
    return list(result.scalars().all())


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

    # Generate the duplicate checker fingerprint (using date to day-level precision to match amount+date+account_last4 requirement)
    raw_str = f"{current_user.id}:{parsed['amount']:.2f}:{parsed['date'].date().isoformat()}:{parsed['account_last4'] or 'unknown'}"
    fingerprint = hashlib.sha256(raw_str.encode("utf-8")).hexdigest()

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
    )

    db.add(new_tx)
    await db.flush()

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

        raw_str = f"{result['amount']:.2f}:{tx_date.date().isoformat()}:{result.get('bank') or 'unknown'}"
        fingerprint = hashlib.sha256(raw_str.encode("utf-8")).hexdigest()

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
