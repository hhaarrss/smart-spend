"""
Utility module for generating deterministic SHA-256 transaction fingerprints.

Strategy (two-tier):
  Tier 1 — UPI/IMPS ref available: hash(user_id + upi_ref)
           UPI refs are globally unique per transaction. Gold standard deduplication.

  Tier 2 — No UPI ref (cash, POS, small transfers): hash(user_id + amount + account_last4 + date_10s_bucket)
           Date is truncated to the nearest 10-second bucket.
           This prevents the app from double-posting the EXACT same SMS twice
           (which would happen within the same 10 seconds), while allowing two
           genuine ₹1 payments made 10+ seconds apart to both be recorded correctly.
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
    user_id + amount + account_last4 + date truncated to 10-second bucket.
    Two ₹1 payments made 10+ seconds apart → different fingerprints → both recorded ✅
    Same SMS sent twice within 10 seconds → same fingerprint → duplicate blocked ✅
    """
    u_ref = (upi_ref or "").strip().lower()

    # --- Tier 1: UPI/IMPS ref is available (globally unique per transaction) ---
    if u_ref and u_ref not in ("none", "null", "na", "n/a"):
        raw_str = f"upi:{user_id}:{u_ref}"
        return hashlib.sha256(raw_str.encode("utf-8")).hexdigest()

    # --- Tier 2: Fallback to amount + account + 10-second time bucket ---
    last4 = (account_last4 or "unknown").strip() or "unknown"

    if isinstance(date_val, datetime):
        # Truncate timestamp to 10-second bucket: e.g. 09:47:53 → 09:47:50
        epoch_seconds = int(date_val.timestamp())
        bucket_10s = (epoch_seconds // 10) * 10
        d_str = str(bucket_10s)
    elif isinstance(date_val, date):
        d_str = date_val.isoformat()
    else:
        d_str = str(date_val)[:16]

    raw_str = f"fallback:{user_id}:{amount:.2f}:{last4}:{d_str}"
    return hashlib.sha256(raw_str.encode("utf-8")).hexdigest()
