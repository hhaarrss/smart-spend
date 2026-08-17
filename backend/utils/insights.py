"""
Insights Utility Engine.

Provides analytical computations for expense tracking, MoM comparisons,
anomaly flagging, subscription discovery, and budget notifications.
"""

from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Optional
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession
from models.transaction import Transaction
from models.budget import BudgetLimit
from categorizer.transaction_categorizer import normalize_category_name
import calendar


async def compare_month_spending(user_id: int, category: str, db: AsyncSession) -> Optional[float]:
    """
    Compares the current month's spending in a category vs the previous month.

    Args:
        user_id (int): ID of the target user.
        category (str): Target expense category.
        db (AsyncSession): Active database session.

    Returns:
        Optional[float]: Percentage change in spending, or None if previous month spending is 0.0.
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
        return None

    return round(((cur_spent - prev_spent) / prev_spent) * 100.0, 2)


async def detect_anomalies(user_id: int, db: AsyncSession) -> List[Dict[str, Any]]:
    """
    Detects anomalies in the current month:
    1. Individual transactions 2x higher than historical category average.
    2. Category budget breaches (spending over 100% of limit).
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
    seen_keys = set()

    for tx in current_transactions:
        cat_lower = tx.category.lower()
        hist_avg = category_averages.get(cat_lower)
        
        # Flag transaction spike if spent exceeds 2x historical average
        if hist_avg and hist_avg >= 100.0:
            tx_amount = float(tx.amount)
            if tx_amount >= 2 * hist_avg:
                key = (tx.merchant or "Unknown", tx_amount)
                if key not in seen_keys:
                    seen_keys.add(key)
                    anomalies.append({
                        "merchant": tx.merchant or "Unknown Merchant",
                        "amount": tx_amount,
                        "avg": round(hist_avg, 2),
                        "category": tx.category,
                        "date": tx.date.isoformat() if hasattr(tx.date, 'isoformat') else str(tx.date)
                    })

    # 3. Check for Category Budget Breaches (>100% limit) and surface them in Anomalies
    budgets_query = select(BudgetLimit).where(BudgetLimit.user_id == user_id)
    budgets_res = await db.execute(budgets_query)
    raw_budgets = budgets_res.scalars().all()

    # Deduplicate budget limits (using normalized category key)
    budgets_map = {}
    for b in raw_budgets:
        cat_key = normalize_category_name(b.category)
        limit_val = float(b.monthly_limit) if b.monthly_limit else 0.0
        if limit_val > 0:
            if cat_key not in budgets_map or limit_val < float(budgets_map[cat_key].monthly_limit):
                budgets_map[cat_key] = b

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
    spent_totals = {}
    for row in spent_res.all():
        c_norm = normalize_category_name(row[0])
        spent_totals[c_norm] = spent_totals.get(c_norm, 0.0) + float(row[1])

    for b in budgets_map.values():
        c_norm = normalize_category_name(b.category)
        spent = spent_totals.get(c_norm, 0.0)
        limit_val = float(b.monthly_limit) if b.monthly_limit else 0.0
        if limit_val > 0 and spent > limit_val:
            breach_key = (f"Budget Exceeded: {c_norm}", spent)
            if breach_key not in seen_keys:
                seen_keys.add(breach_key)
                anomalies.append({
                    "merchant": f"Over Budget ({c_norm})",
                    "amount": round(spent, 2),
                    "avg": limit_val,
                    "category": c_norm,
                    "date": now.isoformat()
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
                
                # Check interval buckets: weekly (6-8 days), monthly (25-35 days), annual (350-380 days)
                day_diff = (tx2.date - tx1.date).days
                frequency = None
                if 6 <= day_diff <= 8:
                    frequency = "weekly"
                elif 25 <= day_diff <= 35:
                    frequency = "monthly"
                elif 350 <= day_diff <= 380:
                    frequency = "annual"

                if frequency:
                    display_merchant = tx2.merchant or merchant_name
                    if not any(r["merchant"].lower() == display_merchant.lower() for r in recurring):
                        recurring.append({
                            "merchant": display_merchant,
                            "amount": amt2,
                            "frequency": frequency
                        })
                    break

    return recurring


async def get_budget_alerts(user_id: int, db: AsyncSession) -> List[Dict[str, Any]]:
    """
    Identifies spending categories in the current month exceeding 80% of configured limits.
    Deduplicates categories so each category appears at most once.

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
    raw_budgets = budgets_res.scalars().all()

    if not raw_budgets:
        return []

    # Deduplicate budget limits by category (using normalized category key)
    budgets_map = {}
    for b in raw_budgets:
        cat_key = normalize_category_name(b.category)
        limit_val = float(b.monthly_limit) if b.monthly_limit else 0.0
        if limit_val > 0:
            if cat_key not in budgets_map or limit_val < float(budgets_map[cat_key].monthly_limit):
                budgets_map[cat_key] = b

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
    spent_totals = {}
    for row in spent_res.all():
        c_norm = normalize_category_name(row[0])
        spent_totals[c_norm] = spent_totals.get(c_norm, 0.0) + float(row[1])

    alerts = []
    for b in budgets_map.values():
        c_norm = normalize_category_name(b.category)
        spent = spent_totals.get(c_norm, 0.0)
        limit_val = float(b.monthly_limit) if b.monthly_limit else 0.0

        # Guard against division by zero
        if limit_val <= 0:
            continue

        pct = (spent / limit_val) * 100
        
        # Trigger alert if spending is greater than or equal to configured alert percentage (defaults to 80%)
        if pct >= (b.alert_at_percent or 80.0):
            alerts.append({
                "category": c_norm,
                "spent": round(spent, 2),
                "limit": limit_val,
                "percent": round(pct, 2)
            })

    return alerts
