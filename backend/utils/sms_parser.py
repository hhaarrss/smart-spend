"""
SMS parser utility for extracting transaction details from bank SMS alerts.

Supports: HDFC, SBI, ICICI, Axis, Kotak, Yes Bank, PNB, Paytm, IDFC, Union, BoB, Canara, RBL, Citi, Federal, Amex, and all Indian UPI payment SMS headers/bodies.
"""

import re
from datetime import datetime
from typing import Optional, Dict, Any


def clean_amount(amt_str: str) -> float:
    """
    Cleans a currency amount string by removing commas, currency symbols, and spaces.
    """
    match = re.search(r"(\d+(?:,\d+)*(?:\.\d+)?)", amt_str)
    if match:
        return float(match.group(1).replace(",", ""))
    return 0.0


def parse_sms_date(date_str: str) -> datetime:
    """
    Parses a date string from an SMS into a datetime object.
    Supports formats: DD-MM-YY, DD-MM-YYYY, DD/MM/YY, DD/MM/YYYY, DD-MMM-YY, DDMMM-YY, etc.
    """
    date_str = date_str.strip().replace(".", "-").replace("/", "-")
    
    formats = [
        "%d-%m-%y", "%d-%m-%Y",
        "%d-%b-%y", "%d-%b-%Y",
        "%d%b%y", "%d%b%Y",
        "%Y-%m-%d", "%d-%B-%Y"
    ]
    
    date_str = re.sub(r"(\d+)(st|nd|rd|th)", r"\1", date_str)
    
    for fmt in formats:
        try:
            return datetime.strptime(date_str, fmt)
        except ValueError:
            continue
            
    date_str_no_sep = re.sub(r"\s+", "", date_str)
    for fmt in ["%d%b%y", "%d%b%Y"]:
        try:
            return datetime.strptime(date_str_no_sep, fmt)
        except ValueError:
            continue

    return datetime.now()


def identify_bank(sender: str, body: str) -> str:
    """
    Identifies the bank or payment provider from sender ID or SMS body.
    Sender ID takes priority over body text to avoid false matches
    (e.g. BOB SMS containing 'oksbi' UPI VPA should not be tagged as SBI).
    """
    s_upper = sender.upper()
    b_upper = body.upper()

    # Check sender first (high confidence)
    if "HDFC" in s_upper: return "HDFC"
    if "SBI" in s_upper: return "SBI"
    if "ICICI" in s_upper: return "ICICI"
    if "AXIS" in s_upper: return "Axis"
    if "KOTAK" in s_upper: return "Kotak"
    if "YESBK" in s_upper or "YESBNK" in s_upper: return "Yes Bank"
    if "PNB" in s_upper: return "PNB"
    if "PAYTM" in s_upper or "PYTM" in s_upper: return "Paytm"
    if "IDFC" in s_upper: return "IDFC"
    if "UNION" in s_upper: return "Union Bank"
    if "BOB" in s_upper or "BARODA" in s_upper: return "Bank of Baroda"
    if "CANARA" in s_upper: return "Canara Bank"
    if "RBL" in s_upper: return "RBL Bank"
    if "CITI" in s_upper: return "Citi Bank"
    if "FED" in s_upper or "FEDERAL" in s_upper: return "Federal Bank"
    if "AMEX" in s_upper: return "AmEx"
    if "GPAY" in s_upper or "BHIM" in s_upper: return "UPI"

    # Fall back to body (lower confidence — avoid UPI VPA false matches)
    if "HDFC BANK" in b_upper: return "HDFC"
    if "STATE BANK" in b_upper or "SBI" in b_upper: return "SBI"
    if "ICICI BANK" in b_upper: return "ICICI"
    if "AXIS BANK" in b_upper: return "Axis"
    if "KOTAK" in b_upper: return "Kotak"
    if "PUNJAB" in b_upper: return "PNB"
    if "BARODA" in b_upper or "BOB" in b_upper: return "Bank of Baroda"
    if "CANARA" in b_upper: return "Canara Bank"
    if "UNION BANK" in b_upper: return "Union Bank"
    if "IDFC" in b_upper: return "IDFC"
    if "FEDERAL BANK" in b_upper: return "Federal Bank"
    if "AMERICAN EXPRESS" in b_upper: return "AmEx"

    return "BANK"


def parse_sms(raw_sms: str, sender: str) -> Optional[Dict[str, Any]]:
    """
    Parses any Indian bank or UPI transaction SMS and extracts relevant details.
    """
    sms = " ".join(raw_sms.split())
    sms_lower = sms.lower()

    # 0. Reject marketing, promotional, OTP, loan offers, and mandate requests
    spam_patterns = [
        r"save\s+(?:rs\.?|inr|₹)",
        r"earn\s+up\s+to",
        r"cashback\s+every",
        r"apply\s+now",
        r"pre-approved",
        r"pre\s+approved",
        r"loan\s+offer",
        r"get\s+(?:flat|up\s+to)\s+(?:rs\.?|inr|₹|\d+%)",
        r"win\s+up\s+to",
        r"lifetime\s+free",
        r"at\s+no\s+extra\s+charge",
        r"play\s+\d+\+\s+games",
        r"pro\s+pass",
        r"voucher",
        r"coupon\s+code",
        r"promo\s+code",
        r"discount\s+on",
        r"mandate\s+collect\s+request",
        r"request\s+for\s+blocking\s+of\s+funds",
        r"otp\s+is",
        r"verification\s+code",
        r"do\s+not\s+share\s+(?:this\s+)?otp",
        r"claim\s+now",
        r"offer\s+ends",
        r"congratulations",
        r"credit\s+card\s+limit",
        r"personal\s+loan"
    ]
    for pat in spam_patterns:
        if re.search(pat, sms_lower):
            return None

    # Determine debit vs credit
    debit_keywords = [
        "debited", "debitted", "dr.", "dr ",
        "spent", "paid", "withdrawn", "payment of", "charge",
        "withdrew", "txn to", "used for", "used at", "transaction of", "sent to", "transfer to",
        "auto debit", "auto-debit", "emi deducted", "emi paid", "mandate executed",
        "purchase of", "purchase at", "pos txn"
    ]
    credit_keywords = [
        "credited", "creditted", "cr.", "cr ",
        "deposited", "received from", "received rs",
        "credited with", "refund of", "money received", "salary credited"
    ]

    is_debit = any(kw in sms_lower for kw in debit_keywords)
    is_credit = any(kw in sms_lower for kw in credit_keywords)

    if not is_debit and not is_credit:
        return None

    tx_type = "debit" if is_debit else "credit"
    bank = identify_bank(sender, raw_sms)

    # 1. Amount Extraction
    amt_match = re.search(
        r"(?:rs\.?|inr|₹)\s*([\d,]+\.?\d*)|([\d,]+\.?\d*)\s*(?:rs\.?|inr|₹)",
        sms,
        re.IGNORECASE
    )
    if not amt_match:
        return None

    amount_str = amt_match.group(1) or amt_match.group(2)
    amount = clean_amount(amount_str)
    if amount <= 0:
        return None

    # 2. Account Last 4 Extraction
    acct_match = re.search(
        r"(?:a/c|acct|ac|account|card|ending|linked)\s*(?:no\.?\s*)?[xX*#]{0,10}(\d{3,4})(?!\d)",
        sms,
        re.IGNORECASE
    ) or re.search(r"(?:xx+|\*{2,})(\d{4})", sms, re.IGNORECASE)
    account_last4 = acct_match.group(1) if acct_match else "0000"

    # 3. Merchant / Payee Extraction
    merchant = "Unknown Merchant"
    merch_patterns = [
        r";?\s*([A-Za-z0-9\s._&\-]+?)\s+credited",
        r"(?:info:|narration:|remarks?:|towards)\s+([A-Za-z0-9\s._&\-]+?)(?:\s+on\b|\s+ref\b|\s+upi\b|\s+val\b|\.|$)",
        r"(?:vpa)\s+([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)",
        r"(?:from|to|at)\s+([A-Za-z0-9\s._&\-]+?)(?:\s+on\b|\s+ref\b|\s+upi\b|\s+val\b|\.|$)"
    ]

    for pat in merch_patterns:
        match = re.search(pat, sms, re.IGNORECASE)
        if match:
            candidate = match.group(1).strip()
            candidate = re.sub(r"^(?:a\s+transaction\s+of|payment\s+of|txn\s+of)\s+", "", candidate, flags=re.IGNORECASE).strip()
            candidate_lower = candidate.lower().strip()
            # Reject pure amounts, currency strings, or generic banking tokens
            is_amount = bool(re.search(r"^(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d+)?", candidate_lower) or re.search(r"^[\d,]+(?:\.\d+)?", candidate_lower))
            is_generic = any(
                candidate_lower == term or candidate_lower.startswith(term + " ") or candidate_lower.startswith(term + "/")
                for term in ("bank", "account", "acct", "a/c", "upi", "ref", "card", "your", "avl bal", "balance", "txn", "transaction", "payment")
            )
            if candidate and len(candidate) > 2 and not is_amount and not is_generic:
                merchant = candidate
                break

    # 4. Date Extraction
    date_match = re.search(
        r"(\d{1,2}[\/\-\.](?:\d{1,2}|[A-Za-z]{3})[\/\-\.]\d{2,4})",
        sms
    )
    parsed_date = parse_sms_date(date_match.group(1)) if date_match else datetime.now()

    # 5. Balance Extraction
    bal = None
    bal_match = re.search(
        r"(?:bal|balance|avail\.?\s*bal|avl\.?\s*bal)[\s:=\-]*(?:rs\.?|inr)?\s*([\d,]+\.?\d*)",
        sms_lower
    )
    if bal_match:
        try:
            bal = clean_amount(bal_match.group(1))
        except Exception:
            pass

    # 6. UPI Ref Extraction
    upi_ref_match = re.search(
        r"(?:upi\s*(?:ref(?:erence)?(?:\s*no\.?)?|id|no\.?)?|imps\s*(?:ref(?:erence)?(?:\s*no\.?)?)?|rrn|ref(?:erence)?(?:\s*no\.?)?)\s*[:\s-]*([A-Z0-9]{8,22})",
        sms,
        re.IGNORECASE
    )
    upi_ref = upi_ref_match.group(1) if upi_ref_match else None

    return {
        "amount": amount,
        "type": tx_type,
        "account_last4": account_last4,
        "merchant": merchant,
        "balance": bal,
        "date": parsed_date,
        "bank": bank,
        "upi_ref": upi_ref
    }
