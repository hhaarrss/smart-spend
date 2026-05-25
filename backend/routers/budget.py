"""
Router for Budget Limit Configurations.

Provides endpoints to create, update, and fetch category spending limits.
"""

from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models.budget import BudgetLimit
from models.user import User
from schemas.budget import BudgetLimitCreate, BudgetLimitResponse
from utils.dependencies import get_current_user

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

    Args:
        budget_in (BudgetLimitCreate): The budget limit parameters.
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Returns:
        BudgetLimit: The updated or new BudgetLimit database object.
    """
    # Check if a limit already exists for the user and category
    query = select(BudgetLimit).where(
        and_(
            BudgetLimit.user_id == current_user.id,
            BudgetLimit.category.ilike(budget_in.category),
        )
    )
    result = await db.execute(query)
    existing_limit = result.scalars().first()

    if existing_limit:
        # Update existing
        existing_limit.monthly_limit = budget_in.monthly_limit
        existing_limit.alert_at_percent = budget_in.alert_at_percent
        existing_limit.is_family_limit = budget_in.is_family_limit
        
        # Save change and return
        await db.flush()
        return existing_limit

    # Create new
    new_limit = BudgetLimit(
        user_id=current_user.id,
        category=budget_in.category,
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

    Args:
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Returns:
        List[BudgetLimit]: List of budget limits matching the criteria.
    """
    # Fetch user's limits
    conditions = [BudgetLimit.user_id == current_user.id]

    # If user belongs to a family, we can also include family-wide limits set by other members
    # (or we can just fetch the user's limits. Let's include both for completeness).
    if current_user.family_id:
        # Subquery or separate clause to pull limits of any member of the family
        # where is_family_limit is True
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
    
    return list(result.scalars().all())
