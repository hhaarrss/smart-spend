"""
Utility module for generating deterministic SHA-256 transaction fingerprints.
"""

import hashlib
from datetime import datetime, date
from typing import Union, Optional


def generate_fingerprint(
    user_id: int,
    amount: float,
    date_val: Union[datetime, date],
    account_last4: Optional[str] = None
) -> str:
    """
    Generates a unique SHA-256 fingerprint for a transaction based on user_id, amount, date, and account_last4.

    Args:
        user_id (int): The ID of the transaction owner.
        amount (float): The transaction amount.
        date_val (Union[datetime, date]): The transaction date.
        account_last4 (Optional[str]): The last 4 digits of the account/card number.

    Returns:
        str: Unique 64-character hexadecimal SHA-256 hash.
    """
    if isinstance(date_val, datetime):
        d_str = date_val.date().isoformat()
    elif isinstance(date_val, date):
        d_str = date_val.isoformat()
    else:
        d_str = str(date_val)[:10]

    last4 = (account_last4 or "unknown").strip()
    if not last4:
        last4 = "unknown"

    raw_str = f"{user_id}:{amount:.2f}:{d_str}:{last4}"
    return hashlib.sha256(raw_str.encode("utf-8")).hexdigest()
