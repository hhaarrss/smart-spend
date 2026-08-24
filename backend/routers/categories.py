"""
Router for Expense Categories.
"""

from typing import Dict, List
from fastapi import APIRouter
from constants.categories import CANONICAL_CATEGORIES

router = APIRouter(prefix="/categories", tags=["Categories"])


@router.get("", summary="Get canonical expense category list")
@router.get("/", summary="Get canonical expense category list", include_in_schema=False)
async def get_categories() -> Dict[str, List[str]]:
    """
    Returns the single canonical list of expense categories.

    Returns:
        Dict[str, List[str]]: Object containing list of canonical categories.
    """
    return {"categories": CANONICAL_CATEGORIES}
