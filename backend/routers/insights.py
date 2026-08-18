"""
Router for Analytical Financial Insights.

Provides aggregate metrics, MoM spending changes, anomalies, and active alerts.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from typing import Dict, List, Any

from database import get_db
from models.user import User
from models.transaction import Transaction
from utils.dependencies import get_current_user
from utils.insights import (
    compare_month_spending,
    detect_anomalies,
    detect_recurring,
    get_budget_alerts
)

router = APIRouter(prefix="/insights", tags=["Insights Engine"])


@router.get(
    "/summary",
    response_model=Dict[str, List[Any]],
    summary="Fetch unified analytical spending insights",
)
async def get_insights_summary(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> Dict[str, List[Any]]:
    """
    Retrieves comparative statistics, anomaly detections, recurring obligations,
    and threshold alerts for the authenticated user session.

    Args:
        current_user (User): Authenticated database user entity.
        db (AsyncSession): Active database session.

    Returns:
        Dict[str, List[Any]]: Unified insights payload.
    """
    try:
        # 1. Fetch categories user has spent on historically
        categories_query = select(Transaction.category).where(
            Transaction.user_id == current_user.id
        ).distinct()
        categories_res = await db.execute(categories_query)
        active_categories = categories_res.scalars().all()

        # 2. Compute spending changes per category
        spending_changes = []
        for category in active_categories:
            change = await compare_month_spending(current_user.id, category, db)
            if change is not None and change != 0.0:
                spending_changes.append({
                    "category": category,
                    "change_percent": abs(change),
                    "direction": "up" if change > 0 else "down"
                })

        # 3. Scan for anomalies
        anomalies = await detect_anomalies(current_user.id, db)

        # 4. Check for subscription patterns
        recurring = await detect_recurring(current_user.id, db)

        # 5. Fetch budget threshold alerts
        budget_alerts = await get_budget_alerts(current_user.id, db)

        return {
            "spending_changes": spending_changes,
            "anomalies": anomalies,
            "recurring": recurring,
            "budget_alerts": budget_alerts
        }
    except Exception as e:
        print(f"[Insights] Error computing insights for user {current_user.id}: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to compute insights: {str(e)}"
        )

