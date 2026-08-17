"""
Router for Expense Categories.
"""

from typing import List
from fastapi import APIRouter
from constants.categories import CANONICAL_CATEGORIES

router = APIRouter(prefix="/categories", tags=["Categories"])


@router.get("", response_model=List[str], summary="Get canonical expense category list")
@router.get("/", response_model=List[str], summary="Get canonical expense category list", include_in_schema=False)
async def get_categories() -> List[str]:
    """
    Returns the single canonical list of expense categories.

    Returns:
        List[str]: List of canonical category strings.
    """
    return CANONICAL_CATEGORIES
