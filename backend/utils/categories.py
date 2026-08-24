"""
Canonical list of categories for SmartSpend.
Single source of truth for backend, React web dashboard, and Android app.
"""

CATEGORIES = [
    "Food",
    "Transport",
    "Shopping",
    "Entertainment",
    "Utilities",
    "Healthcare",
    "Education",
    "Travel",
    "Rent",
    "Transfer",
    "Investment",
    "Salary",
    "Refund",
    "Other"
]

LEGACY_CATEGORY_MAPPING = {
    "food & dining": "Food",
    "groceries": "Food",
    "transportation": "Transport",
    "shopping": "Shopping",
    "entertainment": "Entertainment",
    "utilities": "Utilities",
    "utilities & bills": "Utilities",
    "telecom & recharge": "Utilities",
    "healthcare": "Healthcare",
    "education": "Education",
    "travel": "Travel",
    "rent": "Rent",
    "transfer": "Transfer",
    "investment": "Investment",
    "salary": "Salary",
    "refund": "Refund",
    "miscellaneous": "Other",
    "other": "Other"
}
