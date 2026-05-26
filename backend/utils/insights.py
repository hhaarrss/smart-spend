"""
Insights Utility Engine.

Provides analytical computations for expense tracking, MoM comparisons,
anomaly flagging, subscription discovery, and budget notifications.
"""

from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession
from models.transaction import Transaction
from models.budget import BudgetLimit
import calendar


async def compare_month_spending(user_id: int, category: str, db: AsyncSession) -> float:
    """
    Compares the current month's spending in a category vs the previous month.

    Args:
        user_id (int): ID of the target user.
        category (str): Target expense category.
        db (AsyncSession): Active database session.

    Returns:
        float: Percentage change in spending. Positive for an increase, negative for a decrease.
               Returns 0.0 if there is no historical data.
    """
    now = datetime.now(timezone.utc)
    
    # Current month range
    cur_start = datetime(now.year, now.month, 1, 0, 0, 0, tzinfo=timezone.utc)
    
    # Previous month range
    if now.month == 1:
        prev_start = datetime(now.year - 1, 12, 1, 0, 0, 0, tzinfo=timezone.utc)
        prev_end = datetime(now.year, 1, 1, 0, 0, 0, tzinfo=timezone.utc) - timedelta(microseconds=1)
    else:
        prev_start = datetime(now.year, now.month - 1, 1, 0, 0, 0, tzinfo=timezone.utc)
        prev_end = cur_start - timedelta(microseconds=1)

    # Fetch current month sum
    cur_query = select(func.sum(Transaction.amount)).where(
        and_(
            Transaction.user_id == user_id,
            Transaction.category.ilike(category),
            Transaction.type == "debit",
            Transaction.date >= cur_start,
            Transaction.date <= now
        )
    )
    cur_res = await db.execute(cur_query)
    cur_spent = float(cur_res.scalar() or 0.0)

    # Fetch previous month sum
    prev_query = select(func.sum(Transaction.amount)).where(
        and_(
            Transaction.user_id == user_id,
            Transaction.category.ilike(category),
            Transaction.type == "debit",
            Transaction.date >= prev_start,
            Transaction.date <= prev_end
        )
    )
    prev_res = await db.execute(prev_query)
    prev_spent = float(prev_res.scalar() or 0.0)

    if prev_spent == 0.0:
        return 100.0 if cur_spent > 0.0 else 0.0

    return round(((cur_spent - prev_spent) / prev_spent) * 100.0, 2)


async def detect_anomalies(user_id: int, db: AsyncSession) -> List[Dict[str, Any]]:
    """
    Detects transactions in the current month that are 2x higher than the
    category-wide historical rolling average.

    Args:
        user_id (int): Target owner user ID.
        db (AsyncSession): Active database session.

    Returns:
        List[Dict[str, Any]]: List of flagged anomalous transaction items.
    """
    now = datetime.now(timezone.utc)
    cur_start = datetime(now.year, now.month, 1, 0, 0, 0, tzinfo=timezone.utc)

    # 1. Fetch rolling category averages historically before the current month
    avg_query = (
        select(Transaction.category, func.avg(Transaction.amount))
        .where(
            and_(
                Transaction.user_id == user_id,
                Transaction.type == "debit",
                Transaction.date < cur_start
            )
        )
        .group_by(Transaction.category)
    )
    avg_res = await db.execute(avg_query)
    category_averages = {row[0].lower(): float(row[1]) for row in avg_res.all()}

    # 2. Fetch current month debits
    cur_tx_query = select(Transaction).where(
        and_(
            Transaction.user_id == user_id,
            Transaction.type == "debit",
            Transaction.date >= cur_start
        )
    )
    cur_tx_res = await db.execute(cur_tx_query)
    current_transactions = cur_tx_res.scalars().all()

    anomalies = []
    for tx in current_transactions:
        cat_lower = tx.category.lower()
        hist_avg = category_averages.get(cat_lower)
        
        # Flag as anomaly if spent exceeds 2x category historical average (minimum historical average threshold of 100 to avoid low limit noise)
        if hist_avg and hist_avg >= 100.0:
            tx_amount = float(tx.amount)
            if tx_amount >= 2 * hist_avg:
                anomalies.append({
                    "merchant": tx.merchant or "Unknown Merchant",
                    "amount": tx_amount,
                    "avg": round(hist_avg, 2),
                    "category": tx.category,
                    "date": tx.date.isoformat()
                })

    return anomalies


async def detect_recurring(user_id: int, db: AsyncSession) -> List[Dict[str, Any]]:
    """
    Scans transaction history to identify regular recurring expenses (subscriptions/EMIs).
    Matches based on amount variance (within 5%) and date intervals (25-35 days spacing).

    Args:
        user_id (int): Target user database ID.
        db (AsyncSession): Active database session.

    Returns:
        List[Dict[str, Any]]: Mapped monthly recurring subscriptions.
    """
    # Fetch all historical debits sorted by date ascending
    query = select(Transaction).where(
        and_(
            Transaction.user_id == user_id,
            Transaction.type == "debit"
        )
    ).order_by(Transaction.date.asc())
    
    result = await db.execute(query)
    txs = result.scalars().all()

    # Group transactions by merchant keyword (case-insensitive)
    merchant_groups: Dict[str, List[Transaction]] = {}
    for tx in txs:
        if not tx.merchant:
            continue
        m_lower = tx.merchant.lower().strip()
        if m_lower not in merchant_groups:
            merchant_groups[m_lower] = []
        merchant_groups[m_lower].append(tx)

    recurring = []
    for merchant_name, group in merchant_groups.items():
        if len(group) < 2:
            continue
            
        # Analyze pairs in consecutive windows to find repeating amounts
        for i in range(len(group)):
            for j in range(i + 1, len(group)):
                tx1 = group[i]
                tx2 = group[j]
                
                amt1 = float(tx1.amount)
                amt2 = float(tx2.amount)
                
                # Check 5% tolerance on amounts
                if amt1 == 0:
                    continue
                diff_pct = abs(amt1 - amt2) / amt1
                if diff_pct > 0.05:
                    continue
                
                # Check day spacing (25 to 35 days apart)
                day_diff = (tx2.date - tx1.date).days
                if 25 <= day_diff <= 35:
                    # Flag this as a recurring subscription
                    # Match name casing from the database
                    display_merchant = tx2.merchant or merchant_name
                    # Make sure we don't add duplicate merchant alarms to output
                    if not any(r["merchant"].lower() == display_merchant.lower() for r in recurring):
                        recurring.append({
                            "merchant": display_merchant,
                            "amount": amt2,
                            "frequency": "monthly"
                        })
                    break

    return recurring


async def get_budget_alerts(user_id: int, db: AsyncSession) -> List[Dict[str, Any]]:
    """
    Identifies spending categories in the current month exceeding 80% of configured limits.

    Args:
        user_id (int): Target user database ID.
        db (AsyncSession): Active database session.

    Returns:
        List[Dict[str, Any]]: Configured warning parameters.
    """
    now = datetime.now(timezone.utc)
    cur_start = datetime(now.year, now.month, 1, 0, 0, 0, tzinfo=timezone.utc)

    # 1. Fetch configured budgets
    budgets_query = select(BudgetLimit).where(BudgetLimit.user_id == user_id)
    budgets_res = await db.execute(budgets_query)
    budgets = budgets_res.scalars().all()

    if not budgets:
        return []

    # 2. Fetch spent sums by category for current month
    spent_query = (
        select(Transaction.category, func.sum(Transaction.amount))
        .where(
            and_(
                Transaction.user_id == user_id,
                Transaction.type == "debit",
                Transaction.date >= cur_start
            )
        )
        .group_by(Transaction.category)
    )
    spent_res = await db.execute(spent_query)
    spent_totals = {row[0].lower(): float(row[1]) for row in spent_res.all()}

    alerts = []
    for b in budgets:
        spent = spent_totals.get(b.category.lower(), 0.0)
        pct = (spent / b.monthly_limit) * 100
        
        # Trigger alert if spending is greater than or equal to configured alert percentage (defaults to 80%)
        if pct >= b.alert_at_percent:
            alerts.append({
                "category": b.category,
                "spent": round(spent, 2),
                "limit": float(b.monthly_limit),
                "percent": round(pct, 2)
            })

    return alerts
