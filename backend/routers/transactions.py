"""
Router for Transaction management.

Handles transaction creation, filtering, duplicate prevention, and aggregate summaries.
"""

import calendar
import hashlib
from datetime import datetime, timezone
from typing import Optional, List, Dict
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy import select, and_, func
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models.transaction import Transaction
from models.user import User
from schemas.transaction import TransactionCreate, TransactionResponse, TransactionSummaryResponse
from schemas.sms import SMSIngestionRequest, SMSIngestionResponse
from utils.sms_parser import parse_sms
from utils.categorizer import categorize_merchant
from utils.dependencies import get_current_user

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

    query = select(Transaction).where(and_(*conditions)).order_by(Transaction.date.desc())
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

    # Categorize the merchant
    category = categorize_merchant(parsed["merchant"])

    # Generate the duplicate checker fingerprint (using date to day-level precision to match amount+date+account_last4 requirement)
    raw_str = f"{parsed['amount']:.2f}:{parsed['date'].date().isoformat()}:{parsed['account_last4'] or 'unknown'}"
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
        merchant=parsed["merchant"],
        bank=parsed["bank"],
        account_last4=parsed["account_last4"],
        date=tx_date,
        hash_fingerprint=fingerprint,
        source="sms",
    )

    db.add(new_tx)
    await db.flush()

    return SMSIngestionResponse(
        success=True,
        transaction=new_tx,
        message="SMS ingested successfully"
    )
