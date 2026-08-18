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
    Defaults to "BANK" if no specific institution name matches.
    """
    s_upper = sender.upper()
    b_upper = body.upper()
    
    if "HDFC" in s_upper or "HDFC" in b_upper:
        return "HDFC"
    elif "SBI" in s_upper or "SBI" in b_upper:
        return "SBI"
    elif "ICICI" in s_upper or "ICICI" in b_upper:
        return "ICICI"
    elif "AXIS" in s_upper or "AXIS" in b_upper:
        return "Axis"
    elif "KOTAK" in s_upper or "KOTAK" in b_upper:
        return "Kotak"
    elif "YESBK" in s_upper or "YES" in b_upper:
        return "Yes Bank"
    elif "PNB" in s_upper or "PUNJAB" in b_upper:
        return "PNB"
    elif "PAYTM" in s_upper or "PYTM" in b_upper:
        return "Paytm"
    elif "IDFC" in s_upper or "IDFC" in b_upper:
        return "IDFC"
    elif "UNION" in s_upper or "UNION" in b_upper:
        return "Union Bank"
    elif "BOB" in s_upper or "BARODA" in b_upper:
        return "Bank of Baroda"
    elif "CANARA" in s_upper or "CANARA" in b_upper:
        return "Canara Bank"
    elif "RBL" in s_upper or "RBL" in b_upper:
        return "RBL Bank"
    elif "CITI" in s_upper or "CITI" in b_upper:
        return "Citi Bank"
    elif "FED" in s_upper or "FEDERAL" in b_upper:
        return "Federal Bank"
    elif "AMEX" in s_upper or "AMERICAN EXPRESS" in b_upper:
        return "AmEx"
    
    return "BANK"


def parse_sms(raw_sms: str, sender: str) -> Optional[Dict[str, Any]]:
    """
    Parses any Indian bank or UPI transaction SMS and extracts relevant details.
    """
    sms = " ".join(raw_sms.split())
    sms_lower = sms.lower()

    # Determine debit vs credit
    debit_keywords = [
        "debited", "spent", "paid", "withdrawn", "payment", "charge",
        "withdrew", "txn to", "used for", "used at", "transaction of", "used", "sent to", "transfer to"
    ]
    credit_keywords = [
        "credited", "received", "deposited", "added", "refund", "salary", "cashback"
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
        r"(?:a/c|acct|ac|card|account|vpa)\s*(?:no\.?\s*)?(?:x+|\*+)?(\d{3,4})",
        sms,
        re.IGNORECASE
    )
    account_last4 = acct_match.group(1) if acct_match else "0000"

    # 3. Merchant / Payee Extraction
    merchant = "Unknown Merchant"
    merch_patterns = [
        r"(?:to|at|info:)\s+([A-Za-z0-9\s._&\-]+?)(?:\s+on|\s+ref|\s+upi|\s+val|\.|$)",
        r"(?:vpa|to)\s+([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)",
        r"(?:from)\s+([A-Za-z0-9\s._&\-]+?)(?:\s+on|\s+ref|\s+upi|\.|$)"
    ]

    for pat in merch_patterns:
        match = re.search(pat, sms, re.IGNORECASE)
        if match:
            candidate = match.group(1).strip()
            if candidate and len(candidate) > 2 and candidate.lower() not in ("bank", "account", "upi", "ref"):
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

    return {
        "amount": amount,
        "type": tx_type,
        "account_last4": account_last4,
        "merchant": merchant,
        "balance": bal,
        "date": parsed_date,
        "bank": bank
    }
