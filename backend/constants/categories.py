"""
Single source of truth for canonical category definitions (Debit and Credit).
"""

from typing import List

# 8 Canonical Credit Categories
CREDIT_CATEGORIES: List[str] = [
    "Salary",
    "Refund",
    "Interest",
    "Bank Deposit",
    "Investment Return",
    "Reimbursement",
    "Cashback",
    "Other Credit",
]

# Canonical Debit / Expense Categories
DEBIT_CATEGORIES: List[str] = [
    "Food & Dining",
    "Groceries",
    "Transportation",
    "Shopping",
    "Entertainment",
    "Utilities & Bills",
    "Healthcare",
    "Education",
    "Travel & Hotels",
    "Fuel",
    "Subscriptions",
    "Telecom & Recharge",
    "Finance & Insurance",
    "Personal Care",
    "Rent",
    "Transfer",
    "Other",
]

CANONICAL_CATEGORIES: List[str] = DEBIT_CATEGORIES + CREDIT_CATEGORIES
