"""
Utility module for generating deterministic SHA-256 transaction fingerprints.

Strategy (two-tier):
  Tier 1 — UPI/IMPS ref available: hash(user_id + upi_ref)
           UPI refs are globally unique per transaction. This is the gold standard.
  Tier 2 — No UPI ref: hash(user_id + amount + account_last4 + date_truncated_to_minute)
           Truncating to the minute allows the same amount on the same day to go
           through as long as they are more than 60 seconds apart, while still blocking
           genuine double-posts of the exact same transaction.
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
    Generates a unique SHA-256 fingerprint for a transaction.

    Tier 1 (preferred): If a UPI/IMPS reference number is present, the fingerprint is
    based solely on user_id + upi_ref, which is globally unique per transaction.

    Tier 2 (fallback): If no UPI ref, fingerprint is based on
    user_id + amount + account_last4 + date truncated to the minute.
    This lets two ₹100 payments one minute apart both pass through,
    while still blocking double-posts of the exact same transaction.
    """
    u_ref = (upi_ref or "").strip().lower()

    # --- Tier 1: UPI/IMPS ref is available ---
    if u_ref and u_ref not in ("none", "null", "na", "n/a"):
        raw_str = f"upi:{user_id}:{u_ref}"
        return hashlib.sha256(raw_str.encode("utf-8")).hexdigest()

    # --- Tier 2: Fallback to amount + account + time (truncated to minute) ---
    last4 = (account_last4 or "unknown").strip() or "unknown"

    if isinstance(date_val, datetime):
        # Truncate to the minute to allow same-amount different-time transactions
        d_str = date_val.strftime("%Y-%m-%dT%H:%M")
    elif isinstance(date_val, date):
        d_str = date_val.isoformat()
    else:
        d_str = str(date_val)[:16]  # take up to minute

    raw_str = f"fallback:{user_id}:{amount:.2f}:{last4}:{d_str}"
    return hashlib.sha256(raw_str.encode("utf-8")).hexdigest()
