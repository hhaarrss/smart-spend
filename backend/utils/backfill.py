"""
Utility functions for backfilling normalized categories across legacy database records.
"""

from typing import Dict
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.engine import Connection


CATEGORY_BACKFILL_MAP: Dict[str, str] = {
    # Debit / Expense categories
    "food": "Food & Dining",
    "food & dining": "Food & Dining",
    "food and dining": "Food & Dining",
    "dining": "Food & Dining",
    "travel": "Transportation",
    "travel & hotels": "Travel & Hotels",
    "travel and hotels": "Travel & Hotels",
    "hotels": "Travel & Hotels",
    "hotel": "Travel & Hotels",
    "transportation": "Transportation",
    "cab": "Transportation",
    "fuel": "Fuel",
    "bills": "Utilities & Bills",
    "utilities": "Utilities & Bills",
    "utilities & bills": "Utilities & Bills",
    "groceries": "Groceries",
    "shopping": "Shopping",
    "healthcare": "Healthcare",
    "entertainment": "Entertainment",
    "education": "Education",
    "subscriptions": "Subscriptions",
    "telecom & recharge": "Telecom & Recharge",
    "recharge": "Telecom & Recharge",
    "finance": "Finance & Insurance",
    "finance & insurance": "Finance & Insurance",
    "personal care": "Personal Care",
    "rent": "Rent",
    "transfer": "Transfer",
    "miscellaneous": "Other",
    "other": "Other",
    "needs review": "Needs Review",
    "needs_review": "Needs Review",

    # Credit categories
    "salary": "Salary",
    "refund": "Refund",
    "reversed": "Refund",
    "reversal": "Refund",
    "interest": "Interest",
    "interest credited": "Interest",
    "bank deposit": "Bank Deposit",
    "bank_deposit": "Bank Deposit",
    "deposit": "Bank Deposit",
    "investment return": "Investment Return",
    "investment_return": "Investment Return",
    "investment": "Investment Return",
    "dividend": "Investment Return",
    "reimbursement": "Reimbursement",
    "cashback": "Cashback",
    "reward": "Cashback",
    "rewards": "Cashback",
    "other credit": "Other Credit",
    "other_credit": "Other Credit",
}


async def run_category_backfill_async(session: AsyncSession) -> int:
    """
    Executes category normalization backfill across transactions and merchant_mappings tables asynchronously.

    Args:
        session (AsyncSession): SQLAlchemy async database session.

    Returns:
        int: Total number of rows updated.
    """
    total_updated = 0
    for legacy, canonical in CATEGORY_BACKFILL_MAP.items():
        res_tx = await session.execute(
            text("UPDATE transactions SET category = :canonical WHERE lower(trim(category)) = :legacy"),
            {"canonical": canonical, "legacy": legacy},
        )
        res_mm = await session.execute(
            text("UPDATE merchant_mappings SET category = :canonical WHERE lower(trim(category)) = :legacy"),
            {"canonical": canonical, "legacy": legacy},
        )
        total_updated += (res_tx.rowcount or 0) + (res_mm.rowcount or 0)
    await session.commit()
    return total_updated


def run_category_backfill_sync(connection: Connection) -> int:
    """
    Executes category normalization backfill synchronously for Alembic migrations.

    Args:
        connection (Connection): SQLAlchemy database connection.

    Returns:
        int: Total number of rows updated.
    """
    total_updated = 0
    for legacy, canonical in CATEGORY_BACKFILL_MAP.items():
        res_tx = connection.execute(
            text("UPDATE transactions SET category = :canonical WHERE lower(trim(category)) = :legacy"),
            {"canonical": canonical, "legacy": legacy},
        )
        res_mm = connection.execute(
            text("UPDATE merchant_mappings SET category = :canonical WHERE lower(trim(category)) = :legacy"),
            {"canonical": canonical, "legacy": legacy},
        )
        total_updated += (res_tx.rowcount or 0) + (res_mm.rowcount or 0)
    return total_updated
