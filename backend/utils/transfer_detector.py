"""
P2P UPI Transfer Detection Engine.

Differentiates personal transfers (e.g. sending money to friends/family)
from merchant spending (e.g. Swiggy, Zomato, Amazon, Uber).
"""

import re
from typing import Tuple, Optional

# Known merchant handles that are NOT transfers
KNOWN_MERCHANT_HANDLES = {
    "swiggy", "zomato", "amazon", "flipkart", "irctc", "uber", "ola",
    "netflix", "hotstar", "paytmmerchant", "razorpay", "phonepe",
    "bookmyshow", "bigbasket", "zepto", "blinkit", "instamart",
    "d mart", "reliance", "tatacliq", "make_my_trip", "makemytrip",
    "goibibo", "redbus", "myntra", "nykaa", "ajio"
}

# Personal VPA provider handles
PERSONAL_VPA_DOMAINS = [
    r"@okaxis$", r"@ybl$", r"@ibl$", r"@paytm$", r"@upi$",
    r"@okicici$", r"@oksbi$", r"@barodampay$", r"@postbank$",
    r"@axl$", r"@apl$", r"@ptaxis$", r"@ptyes$", r"@waaxis$"
]


def is_known_merchant(text: str) -> bool:
    """
    Checks if a merchant string or handle matches a known commercial entity.
    """
    t_lower = text.lower()
    for m in KNOWN_MERCHANT_HANDLES:
        if m in t_lower:
            return True
    return False


def detect_p2p_transfer(merchant: Optional[str], raw_sms: Optional[str] = None, category: Optional[str] = None) -> Tuple[bool, Optional[str]]:
    """
    Determines if a transaction is a P2P transfer based on merchant handle, VPA patterns, and SMS phrasing.

    Returns:
        Tuple[bool, Optional[str]]: (is_transfer, transfer_to_name)
    """
    merchant_str = (merchant or "").strip()
    sms_str = (raw_sms or "").strip().lower()

    # Rule 1: Exclude known merchants immediately
    if merchant_str and is_known_merchant(merchant_str):
        return False, None

    # Rule 2: VPA / Handle pattern check
    if "@" in merchant_str:
        vpa_parts = merchant_str.lower().split("@")
        handle = vpa_parts[0]
        domain = "@" + vpa_parts[1] if len(vpa_parts) > 1 else ""

        if not is_known_merchant(handle):
            for pat in PERSONAL_VPA_DOMAINS:
                if re.search(pat, domain):
                    # Clean up handle for display e.g. "rahul.sharma@okaxis" -> "Rahul Sharma"
                    cleaned_name = handle.replace(".", " ").replace("_", " ").title()
                    return True, cleaned_name

    # Rule 3: Category match
    if category and category.lower() == "transfer":
        name = merchant_str.split("@")[0].replace(".", " ").replace("_", " ").title() if merchant_str else "Person"
        return True, name

    # Rule 4: SMS phrasing check ("paid to", "sent to", "transferred to")
    transfer_phrases = [
        r"(?:paid|sent|transferred|credited)(?:\s+(?:rs\.?|inr|₹)?\s*[\d,]+\.?\d*)?\s+to\s+([A-Za-z0-9\s._\-]+?)(?:\s+on|\s+ref|\s+upi|\.|$)",
        r"vpa\s+([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)"
    ]

    for pat in transfer_phrases:
        match = re.search(pat, sms_str, re.IGNORECASE)
        if match:
            candidate = match.group(1).strip()
            if candidate and not is_known_merchant(candidate):
                return True, candidate.title()

    # Rule 4: Explicit category match
    if merchant_str.lower() in ("transfer", "p2p", "peer to peer", "friend", "family"):
        return True, merchant_str.title()

    return False, None
