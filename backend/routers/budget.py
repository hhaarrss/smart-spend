"""
Router for Budget Limit Configurations.

Provides endpoints to create, update, and fetch category spending limits.
Also exposes a /utilization endpoint backed by the shared service layer.
"""

from datetime import datetime, timezone
from typing import List, Any, Dict, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models.budget import BudgetLimit
from models.user import User
from schemas.budget import BudgetLimitCreate, BudgetLimitResponse
from utils.dependencies import get_current_user
from services.transaction_aggregates import get_budget_utilization, BudgetStatus

from categorizer.transaction_categorizer import normalize_category_name

router = APIRouter(prefix="/budget", tags=["Budget Limits"])


@router.post(
    "/",
    response_model=BudgetLimitResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Set or update a budget limit",
)
async def set_budget_limit(
    budget_in: BudgetLimitCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> BudgetLimit:
    """
    Sets a monthly budget spending limit for a specific category.
    If a limit already exists for that category, updates it; otherwise, creates a new record.
    """
    norm_cat = normalize_category_name(budget_in.category)

    # Check if a limit already exists for the user and category
    query = select(BudgetLimit).where(
        and_(
            BudgetLimit.user_id == current_user.id,
            BudgetLimit.category.ilike(norm_cat),
        )
    )
    result = await db.execute(query)
    existing_limit = result.scalars().first()

    if existing_limit:
        # Update existing
        existing_limit.category = norm_cat
        existing_limit.monthly_limit = budget_in.monthly_limit
        existing_limit.alert_at_percent = budget_in.alert_at_percent
        existing_limit.is_family_limit = budget_in.is_family_limit
        
        await db.flush()
        return existing_limit

    # Create new
    new_limit = BudgetLimit(
        user_id=current_user.id,
        category=norm_cat,
        monthly_limit=budget_in.monthly_limit,
        alert_at_percent=budget_in.alert_at_percent,
        is_family_limit=budget_in.is_family_limit,
    )

    db.add(new_limit)
    await db.flush()
    
    return new_limit


@router.get(
    "/",
    response_model=List[BudgetLimitResponse],
    summary="Retrieve all budget limits configured by the user",
)
async def get_budget_limits(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> List[BudgetLimit]:
    """
    Lists all budget limits registered under the current user.
    Also returns family-wide budgets if the user belongs to a family group.
    """
    if current_user.family_id:
        family_member_query = select(User.id).where(User.family_id == current_user.family_id)
        res_ids = await db.execute(family_member_query)
        member_ids = list(res_ids.scalars().all())
        
        query = select(BudgetLimit).where(
            and_(
                BudgetLimit.user_id.in_(member_ids),
                BudgetLimit.is_family_limit == True
            ) | (BudgetLimit.user_id == current_user.id)
        )
    else:
        query = select(BudgetLimit).where(BudgetLimit.user_id == current_user.id)

    result = await db.execute(query)

    unique_budgets = {}
    for budget in result.scalars().all():
        key = normalize_category_name(budget.category).lower()
        existing = unique_budgets.get(key)
        if existing is None or budget.user_id == current_user.id:
            unique_budgets[key] = budget

    return list(unique_budgets.values())


@router.get(
    "/utilization",
    response_model=List[Dict[str, Any]],
    summary="Get live budget utilization for a given month from the shared service layer",
)
async def get_budget_utilization_endpoint(
    month: Optional[int] = Query(None, ge=1, le=12, description="Target month (1-12). Defaults to current month."),
    year: Optional[int] = Query(None, ge=2020, le=2030, description="Target year. Defaults to current year."),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> List[Dict[str, Any]]:
    """
    Returns live category budget utilization computed by the shared service layer.
    Each entry contains: category, spent, limit, percent_used, is_alert, is_family_limit.

    Rules (enforced by get_budget_utilization):
    - category names are normalized via normalize_category_name().
    - spent is computed from live transactions table debits only (no cache).
    - Placeholder categories (needs_review, other, etc.) are excluded.

    Args:
        month (Optional[int]): Target month (1-12). Defaults to current UTC month.
        year (Optional[int]): Target year. Defaults to current UTC year.
        current_user (User): Authenticated user.
        db (AsyncSession): Active database session.

    Returns:
        List[Dict[str, Any]]: Sorted list of budget utilization status objects.
    """
    now = datetime.now(timezone.utc)
    target_month = month if month is not None else now.month
    target_year = year if year is not None else now.year

    statuses = await get_budget_utilization(db, current_user.id, target_year, target_month)
    return [
        {
            "category": s.category,
            "spent": s.spent,
            "limit": s.limit,
            "percent_used": s.percent_used,
            "is_alert": s.is_alert,
            "is_family_limit": s.is_family_limit,
        }
        for s in statuses
    ]
