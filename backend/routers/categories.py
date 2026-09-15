"""
Router for Transaction Categories (Debit and Credit).
"""

from typing import Dict, List
from fastapi import APIRouter
from constants.categories import DEBIT_CATEGORIES, CREDIT_CATEGORIES

router = APIRouter(prefix="/categories", tags=["Categories"])


@router.get("", summary="Get canonical debit and credit category lists")
@router.get("/", summary="Get canonical debit and credit category lists", include_in_schema=False)
async def get_categories() -> Dict[str, List[str]]:
    """
    Returns canonical category lists partitioned into debit and credit categories.

    Returns:
        Dict[str, List[str]]: Object containing 'debit' and 'credit' category lists.
    """
    return {
        "debit": DEBIT_CATEGORIES,
        "credit": CREDIT_CATEGORIES,
    }
