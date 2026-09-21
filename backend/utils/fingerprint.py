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
    account_last4: Optional[str] = None,
    merchant: Optional[str] = None,
    upi_ref: Optional[str] = None
) -> str:
    """
    Generates a unique SHA-256 fingerprint for a transaction based on user_id, amount, date, account, merchant, and upi_ref.
    """
    if isinstance(date_val, datetime):
        d_str = date_val.isoformat()
    elif isinstance(date_val, date):
        d_str = date_val.isoformat()
    else:
        d_str = str(date_val)

    last4 = (account_last4 or "unknown").strip()
    if not last4:
        last4 = "unknown"

    m_str = (merchant or "unknown").strip().lower()
    if not m_str:
        m_str = "unknown"

    u_ref = (upi_ref or "none").strip().lower()
    if not u_ref:
        u_ref = "none"

    raw_str = f"{user_id}:{amount:.2f}:{d_str}:{last4}:{m_str}:{u_ref}"
    return hashlib.sha256(raw_str.encode("utf-8")).hexdigest()
