"""
SMS parser utility for extracting transaction details from bank SMS alerts.

Supports: HDFC, SBI, ICICI, Axis, Kotak, Yes Bank, PNB.
"""

import re
from datetime import datetime
from typing import Optional, Dict, Any


def clean_amount(amt_str: str) -> float:
    """
    Cleans a currency amount string by removing commas, currency symbols, and spaces.

    Args:
        amt_str (str): The raw amount string.

    Returns:
        float: Cleaned floating point representation of the amount.
    """
    cleaned = re.sub(r"[^\d.]", "", amt_str)
    return float(cleaned) if cleaned else 0.0


def parse_sms_date(date_str: str) -> datetime:
    """
    Parses a date string from an SMS into a datetime object.
    Supports formats: DD-MM-YY, DD-MM-YYYY, DD/MM/YY, DD/MM/YYYY, DD-MMM-YY, DDMMM-YY, etc.

    Args:
        date_str (str): The raw date string.

    Returns:
        datetime: Timezone-naive datetime object (defaults to current time if parsing fails).
    """
    date_str = date_str.strip().replace(".", "-").replace("/", "-")
    
    # Try various common formats
    formats = [
        "%d-%m-%y", "%d-%m-%Y",
        "%d-%b-%y", "%d-%b-%Y",
        "%d%b%y", "%d%b%Y",
        "%Y-%m-%d", "%d-%B-%Y"
    ]
    
    # Remove ordinal suffixes if any (e.g. 26th)
    date_str = re.sub(r"(\d+)(st|nd|rd|th)", r"\1", date_str)
    
    for fmt in formats:
        try:
            return datetime.strptime(date_str, fmt)
        except ValueError:
            continue
            
    # Try month name without separators, e.g. 26May26
    date_str_no_sep = re.sub(r"\s+", "", date_str)
    for fmt in ["%d%b%y", "%d%b%Y"]:
        try:
            return datetime.strptime(date_str_no_sep, fmt)
        except ValueError:
            continue

    # Fallback to current datetime
    return datetime.now()


def identify_bank(sender: str, body: str) -> Optional[str]:
    """
    Identifies which of the 7 supported Indian banks the SMS is from.

    Args:
        sender (str): The SMS sender ID.
        body (str): The raw text of the SMS.

    Returns:
        Optional[str]: The identified bank name, or None if not supported.
    """
    sender_upper = sender.upper()
    body_upper = body.upper()
    
    if "HDFC" in sender_upper or "HDFC" in body_upper:
        return "HDFC"
    elif "SBI" in sender_upper or "SBI" in body_upper:
        return "SBI"
    elif "ICICI" in sender_upper or "ICICI" in body_upper:
        return "ICICI"
    elif "AXIS" in sender_upper or "AXIS" in body_upper:
        return "Axis"
    elif "KOTAK" in sender_upper or "KOTAK" in body_upper:
        return "Kotak"
    elif "YESBK" in sender_upper or "YES BANK" in body_upper or "YESBANK" in sender_upper:
        return "Yes Bank"
    elif "PNB" in sender_upper or "PNB" in body_upper or "PUNJAB NATIONAL" in body_upper:
        return "PNB"
    
    return None


def parse_sms(raw_sms: str, sender: str) -> Optional[Dict[str, Any]]:
    """
    Parses a bank transaction SMS and extracts relevant details.

    Args:
        raw_sms (str): The raw body content of the SMS.
        sender (str): The sender identifier of the SMS.

    Returns:
        Optional[Dict[str, Any]]: A dictionary containing the parsed transaction details:
            - amount (float)
            - type (str): 'debit' or 'credit'
            - account_last4 (str)
            - merchant (Optional[str])
            - balance (Optional[float])
            - date (datetime)
            - bank (str)
            Or None if the SMS is not a transaction alert from a supported bank.
    """
    bank = identify_bank(sender, raw_sms)
    if not bank:
        return None

    # Normalise whitespace
    sms = " ".join(raw_sms.split())
    sms_lower = sms.lower()

    # Determine debit vs credit
    # Standard keywords
    debit_keywords = ["debited", "spent", "paid", "withdrawn", "payment", "charge", "withdrew", "txn to"]
    credit_keywords = ["credited", "received", "deposited", "added", "refund", "salary"]

    is_debit = any(kw in sms_lower for kw in debit_keywords)
    is_credit = any(kw in sms_lower for kw in credit_keywords)

    if not is_debit and not is_credit:
        # Not a transaction alert
        return None

    tx_type = "debit" if is_debit else "credit"

    # ── ICICI-specific patterns (early return) ──
    # Format 1 (Debit): "ICICI Bank Acct XX373 debited for Rs 5.00 on 28-May-26; pratiktodkar817 credited. UPI:..."
    # Format 2 (Credit): "credited with Rs 160.00 on 28-May-26 from BHAVESH GAUTAM . UPI:...-ICICI Bank."
    if bank == "ICICI":
        icici_debit = "debited" in sms_lower
        icici_credit = "credited with" in sms_lower

        if icici_debit or icici_credit:
            # Account: extract only digits after XX (e.g. XX373 → 373)
            acct_match = re.search(r"(?:Acct|a/c|ac)\s*(?:no\.?\s*)?XX(\d+)", sms, re.IGNORECASE)
            acct = acct_match.group(1) if acct_match else "0000"

            # Date: DD-Mon-YY format (e.g. 28-May-26)
            date_match = re.search(r"on\s+(\d{1,2}-[A-Za-z]{3}-\d{2,4})", sms, re.IGNORECASE)
            date_str = date_match.group(1) if date_match else None
            parsed_date = parse_sms_date(date_str) if date_str else datetime.now()

            # Balance (generic extraction)
            bal = None
            bal_match = re.search(
                r"(?:bal|balance|avail\.?\s*bal|avl\.?\s*bal|available\s*bal)[\s:=\-]*(?:rs\.?|inr)?\s*([\d,]+\.?\d*)",
                sms_lower
            )
            if bal_match:
                try:
                    bal = clean_amount(bal_match.group(1))
                except ValueError:
                    pass

            if icici_debit:
                # Amount after "debited for Rs"
                amt_match = re.search(r"debited\s+for\s+Rs\.?\s*([\d,]+\.?\d*)", sms, re.IGNORECASE)
                # Merchant: word(s) before "credited", after semicolon
                # "...on 28-May-26; pratiktodkar817 credited."
                merch_match = re.search(r";\s*(.+?)\s+credited", sms, re.IGNORECASE)
                merch = merch_match.group(1).strip() if merch_match else "Unknown Merchant"

                if amt_match:
                    return {
                        "amount": clean_amount(amt_match.group(1)),
                        "type": "debit",
                        "account_last4": acct,
                        "merchant": merch,
                        "balance": bal,
                        "date": parsed_date,
                        "bank": bank,
                    }

            if icici_credit:
                # Amount after "credited with Rs"
                amt_match = re.search(r"credited\s+with\s+Rs\.?\s*([\d,]+\.?\d*)", sms, re.IGNORECASE)
                # Merchant: text after "from" up to period, UPI ref, or end
                merch_match = re.search(r"from\s+(.+?)\s*(?:\.|UPI|$)", sms, re.IGNORECASE)
                merch = merch_match.group(1).strip() if merch_match else "Unknown Sender"

                if amt_match:
                    return {
                        "amount": clean_amount(amt_match.group(1)),
                        "type": "credit",
                        "account_last4": acct,
                        "merchant": merch,
                        "balance": bal,
                        "date": parsed_date,
                        "bank": bank,
                    }

    # Extract Account Last 4
    account_match = re.search(
        r"(?:a/c|ac|account|card|ending|xx)\s*(?:no\.?\s*)?(?:ending\s*)?(\d{4})",
        sms_lower
    )
    # Fallback to general 4 digits near A/c / card
    if not account_match:
        account_match = re.search(r"\b\d{4}\b", sms_lower)
    
    account_last4 = account_match.group(1) if account_match else "0000"

    # Extract Amount
    # Typical amount: Rs. 150.00, Rs 150, INR 150.00, Rs.150
    amount_match = re.search(
        r"(?:rs\.?|inr)\s*([\d,]+\.?\d*)",
        sms_lower
    )
    if not amount_match:
        # Fallback to any decimal number resembling transaction amount
        amount_match = re.search(r"\b\d+(?:\.\d{2})?\b", sms_lower)

    if not amount_match:
        return None

    amount = clean_amount(amount_match.group(1))
    if amount <= 0.0:
        return None

    # Extract Balance
    # Look for "bal" or "balance" followed by a number
    balance = None
    balance_match = re.search(
        r"(?:bal|balance|avail bal|avail\. bal|available bal|available balance|avail\. balance)\s*[:\-=\s]*\s*(?:rs\.?|inr)?\s*([\d,]+\.?\d*)",
        sms_lower
    )
    if balance_match:
        try:
            balance = clean_amount(balance_match.group(1))
        except ValueError:
            pass

    # Extract Date
    # Look for patterns like DD-MM-YY, DD/MM/YYYY, etc.
    # Also handles formats like "26May26" or "26-May-26" or "26-05-2026"
    date = datetime.now()
    date_match = re.search(
        r"(\d{2}[-/]\d{2}[-/]\d{2,4}|\d{2}[-/][a-zA-z]{3}[-/]\d{2,4}|\d{2}\s?[a-zA-Z]{3}\s?\d{2,4})",
        sms
    )
    if date_match:
        date = parse_sms_date(date_match.group(1))

    # Extract Merchant
    merchant = None
    # Merchants typically appear after prepositions: to, at, in, info:
    # E.g. "... debited to Swiggy ...", "... spent at Flipkart ..."
    merchant_match = re.search(
        r"(?:to|at|in|info:|by|towards)\s+(?!(?:a/c|ac|account|card|ending|rs\.?|inr|\d+)\b)([A-Za-z0-9\s\-&'\*\/]{3,30}?)(?:\s+using|\s+on|\s+bal|\s+via|\.|$|\n)",
        sms,
        re.IGNORECASE
    )
    if merchant_match:
        merchant = merchant_match.group(1).strip()
        # Clean up common noise words
        merchant = re.sub(r"\s*(?:a/c|card|using|ending|avail|bal|vpa|upi).*$", "", merchant, flags=re.IGNORECASE).strip()
    
    # Fallback merchant based on credit context if none identified
    if not merchant:
        if tx_type == "credit":
            merchant = "Salary/Deposit"
        else:
            merchant = "Unknown Merchant"

    return {
        "amount": amount,
        "type": tx_type,
        "account_last4": account_last4,
        "merchant": merchant,
        "balance": balance,
        "date": date,
        "bank": bank
    }
