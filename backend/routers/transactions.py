"""
Router for Transaction management.

Handles transaction creation, filtering, duplicate prevention, and aggregate summaries.
"""

import hashlib
from datetime import datetime
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy import select, and_, func
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models.transaction import Transaction
from models.user import User
from schemas.transaction import TransactionCreate, TransactionResponse, TransactionSummaryResponse
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

    query = select(Transaction).where(and_(*conditions)).order_by(Transaction.date.desc())
    result = await db.execute(query)
    
    return list(result.scalars().all())


@router.get(
    "/summary",
    response_model=TransactionSummaryResponse,
    summary="Get daily, monthly, and yearly transaction aggregates",
)
async def get_summary(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> TransactionSummaryResponse:
    """
    Computes aggregated expense/income totals grouped by day, month, and year.

    Args:
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Returns:
        TransactionSummaryResponse: Structured object of grouped transaction sums.
    """
    # Fetch all transactions for simple processing
    # (In high scale, direct db-level string formatting or group by can be used, but this is a portable solution)
    query = select(Transaction).where(Transaction.user_id == current_user.id)
    result = await db.execute(query)
    txs = result.scalars().all()

    daily = {}
    monthly = {}
    yearly = {}

    for tx in txs:
        # Determine multiplier depending on type (debit is negative, credit is positive)
        # Or alternatively just sum absolute values. Let's sum debit values as positive expenses
        # to see spending trends, or treat debit as negative. Let's make it the net balance
        # or simple spending sum. Let's calculate simple net balance for comprehensive summaries.
        multiplier = 1.0 if tx.type == "credit" else -1.0
        val = float(tx.amount) * multiplier

        day_key = tx.date.strftime("%Y-%m-%d")
        month_key = tx.date.strftime("%Y-%m")
        year_key = tx.date.strftime("%Y")

        daily[day_key] = daily.get(day_key, 0.0) + val
        monthly[month_key] = monthly.get(month_key, 0.0) + val
        yearly[year_key] = yearly.get(year_key, 0.0) + val

    return TransactionSummaryResponse(daily=daily, monthly=monthly, yearly=yearly)
