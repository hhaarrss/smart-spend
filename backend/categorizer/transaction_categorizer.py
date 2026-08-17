"""
UPI SMS Transaction Parser + Merchant Category Matcher.

Flow:
  SMS -> Extract transaction info -> Match merchant -> Get category
  -> If no match -> "Miscellaneous" (user can reclassify)

Data sources used:
  1. merchants.json      - Top 300 Indian merchants (local DB)
  2. mcc_codes.json      - greggles/mcc-codes (ISO MCC standard)
  3. user_corrections.json - Saved user re-categorizations (grows over time)
"""

import json
import os
import re
from datetime import datetime
from typing import Any, Dict, List, Optional

# Load data files path relative to this file
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
MERCHANTS_PATH = os.path.join(DATA_DIR, "merchants.json")
MCC_PATH = os.path.join(DATA_DIR, "mcc_codes.json")
CORRECTIONS_PATH = os.path.join(DATA_DIR, "user_corrections.json")

MERCHANTS: List[Dict[str, Any]] = []
MCC_CODES: List[Dict[str, Any]] = []
USER_CORRECTIONS: Dict[str, Any] = {}


def load_data() -> None:
    """
    Load data files including merchants, MCC codes, and user corrections.
    """
    global MERCHANTS, MCC_CODES, USER_CORRECTIONS
    try:
        if os.path.exists(MERCHANTS_PATH):
            with open(MERCHANTS_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
                MERCHANTS = data.get("merchants", [])
                print(f"[OK] Loaded {len(MERCHANTS)} merchants")

        if os.path.exists(MCC_PATH):
            with open(MCC_PATH, "r", encoding="utf-8") as f:
                MCC_CODES = json.load(f)
                print(f"[OK] Loaded {len(MCC_CODES)} MCC codes")

        if os.path.exists(CORRECTIONS_PATH):
            with open(CORRECTIONS_PATH, "r", encoding="utf-8") as f:
                USER_CORRECTIONS = json.load(f)
                print(f"[OK] Loaded {len(USER_CORRECTIONS)} user corrections")
    except Exception as err:
        print(f"Error loading data files: {err}")


# Initialize data on import
load_data()


# ─────────────────────────────────────────────
# 2. SMS PARSER
# Extracts: amount, merchant, type (debit/credit), UPI ref, bank
# ─────────────────────────────────────────────

def is_generic_bank_text(text: str) -> bool:
    """
    Filter out non-merchant text like "YOUR A/C", "SBI BANK", etc.
    """
    generic_terms = [
        'YOUR', 'A/C', 'ACCOUNT', 'BANK', 'BALANCE', 'AVAILABLE',
        'HDFC', 'SBI', 'ICICI', 'AXIS', 'KOTAK', 'YES BANK',
        'PAYMENT', 'TRANSFER', 'TRANSACTION', 'UPI', 'IMPS', 'NEFT'
    ]
    return any(term in text for term in generic_terms)


def parse_sms(sms: str) -> Optional[Dict[str, Any]]:
    """
    Parse a raw bank SMS and return structured transaction data.
    """
    if not sms or not isinstance(sms, str):
        return None

    normalized = sms.upper().strip()

    parsed = {
        "raw": sms,
        "amount": None,
        "merchant_raw": None,
        "type": None,       # 'debit' | 'credit'
        "upi_ref": None,
        "bank": None,
        "date": None,
    }

    # Extract amount (standalone pass)
    amount_match = re.search(r"(?:RS\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)", normalized, re.IGNORECASE)
    if amount_match:
        try:
            parsed["amount"] = float(amount_match.group(1).replace(",", ""))
        except ValueError:
            pass

    # Extract UPI reference number
    upi_ref_match = re.search(
        r"(?:UPI\s*(?:REF|TXNID|ID|NO)?|REF\.?\s*NO\.?|TXN\s*(?:ID|NO)?)[:\s\-]*([\d]{6,20})",
        normalized,
        re.IGNORECASE
    )
    if upi_ref_match:
        parsed["upi_ref"] = upi_ref_match.group(1)

    # Extract date
    date_match = re.search(
        r"(\d{1,2}[-\/]\d{1,2}[-\/]\d{2,4}|\d{1,2}\s+(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\s*\d{2,4})",
        normalized,
        re.IGNORECASE
    )
    if date_match:
        parsed["date"] = date_match.group(1)

    # Detect credit vs debit
    if re.search(r"CREDITED|RECEIVED|CREDIT", normalized, re.IGNORECASE):
        parsed["type"] = "credit"
    elif re.search(r"DEBITED|DEDUCTED|SENT|PAID|PURCHASE|SPENT", normalized, re.IGNORECASE):
        parsed["type"] = "debit"

    # Extract merchant name from common UPI SMS info fields
    info_patterns = [
        r"(?:info|remarks?|particulars?|narration)[:\s\-]+(?:UPI[\-\/])?([A-Z][A-Z0-9\s\.\-@&]{2,40}?)(?:[\-\/][\w]+)?(?:\s+ref|\s+upi|\.|,|$)",
        r"(?:to|towards|for|merchant)[:\s]+([A-Z][A-Z0-9\s\.\-@&]{2,40}?)(?:\s+via|\s+on|\s+ref|\.|,|$)",
        r"UPI[\-\/]([\w\s\.\-@&]+?)[\-\/]",
        r"(?:paid to|sent to|transferred to)[:\s]+([A-Z][A-Z0-9\s\.\-@&]{2,40}?)(?:\s+via|\s+on|\s+ref|\.|,|$)"
    ]

    for pattern in info_patterns:
        match = re.search(pattern, normalized, re.IGNORECASE)
        if match and match.group(1):
            candidate = match.group(1).strip()
            if not is_generic_bank_text(candidate):
                parsed["merchant_raw"] = candidate
                break

    return parsed


# ─────────────────────────────────────────────
# 3. MERCHANT MATCHING ENGINE
# Priority: User Corrections -> Merchant DB -> MCC Codes -> Miscellaneous
# ─────────────────────────────────────────────

def normalize_name(name: str) -> str:
    """
    Normalize merchant name for comparison.
    Removes special chars, extra spaces, common suffixes.
    """
    name_upper = name.upper()
    name_upper = re.sub(r"\bPVT\.?\s*LTD\.?\b", "", name_upper)
    name_upper = re.sub(r"\bLIMITED\b", "", name_upper)
    name_upper = re.sub(r"\bPRIVATE\b", "", name_upper)
    name_upper = re.sub(r"\bINDIA\b", "", name_upper)
    name_upper = re.sub(r"\bTECHNOLOGIES\b", "", name_upper)
    name_upper = re.sub(r"[^A-Z0-9\s]", "", name_upper)
    name_upper = re.sub(r"\s+", " ", name_upper)
    return name_upper.strip()


def fuzzy_score(query: str, target: str) -> int:
    """
    Fuzzy match: checks if query is contained in target or vice versa.
    Returns a score 0-100.
    """
    q = normalize_name(query)
    t = normalize_name(target)

    if q == t:
        return 100
    if q in t or t in q:
        return 90

    # Check word overlap
    q_words = [w for w in q.split(" ") if len(w) > 2]
    t_words = [w for w in t.split(" ") if len(w) > 2]

    if not q_words or not t_words:
        return 0

    overlap = len([w for w in q_words if w in t_words])
    if overlap > 0:
        return round((overlap / max(len(q_words), len(t_words))) * 80)

    return 0


def match_user_corrections(merchant_raw: str) -> Optional[Dict[str, Any]]:
    """
    Check user corrections first.
    If a user has re-categorized this merchant before -> trust that.
    """
    key = normalize_name(merchant_raw)
    if key in USER_CORRECTIONS:
        correction = USER_CORRECTIONS[key]
        if correction.get("count", 0) >= 1:
            return {
                "category": correction["category"],
                "subcategory": correction.get("subcategory"),
                "merchant": correction.get("merchant_display", merchant_raw),
                "source": "user_correction",
                "confidence": "high",
            }
    return None


def match_merchant_db(merchant_raw: str) -> Optional[Dict[str, Any]]:
    """
    Match against Top 300 Indian Merchants DB.
    """
    best_match = None
    best_score = 0
    THRESHOLD = 60

    for merchant in MERCHANTS:
        name_score = fuzzy_score(merchant_raw, merchant.get("name", ""))
        if name_score > best_score:
            best_score = name_score
            best_match = merchant

        for alias in merchant.get("aliases", []):
            alias_score = fuzzy_score(merchant_raw, alias)
            if alias_score > best_score:
                best_score = alias_score
                best_match = merchant

        if best_score == 100:
            break

    if best_match and best_score >= THRESHOLD:
        return {
            "category": best_match["category"],
            "subcategory": best_match.get("subcategory"),
            "merchant": best_match["name"],
            "source": "merchant_db",
            "confidence": "high" if best_score >= 90 else "medium",
            "score": best_score,
        }

    return None


def mcc_description_to_category(description: str) -> str:
    """
    Map MCC description to our app's category names.
    """
    desc = description.upper()
    if re.search(r"GROCERY|SUPERMARKET|FOOD STORE", desc):
        return "Groceries"
    if re.search(r"RESTAURANT|EATING|FAST FOOD|PIZZA|BURGER|CAFE", desc):
        return "Food & Dining"
    if re.search(r"AIRLINE|AVIATION|AIRPORT|HOTEL|MOTEL|LODGING", desc):
        return "Travel & Hotels"
    if re.search(r"FUEL|PETROL|GAS STATION|SERVICE STATION", desc):
        return "Fuel"
    if re.search(r"PHARMACY|DRUG STORE|MEDICAL|HEALTH|HOSPITAL|CLINIC|DOCTOR", desc):
        return "Healthcare"
    if re.search(r"ELECTRIC|UTILITY|WATER|GAS", desc):
        return "Utilities & Bills"
    if re.search(r"TELECOM|PHONE|WIRELESS|MOBILE", desc):
        return "Telecom & Recharge"
    if re.search(r"ENTERTAINMENT|MOVIE|CINEMA|AMUSEMENT", desc):
        return "Entertainment"
    if re.search(r"EDUCATION|SCHOOL|COLLEGE|UNIVERSITY", desc):
        return "Education"
    if re.search(r"INSURANCE|LIFE INSURANCE", desc):
        return "Finance & Insurance"
    if re.search(r"TRANSPORT|TAXI|VEHICLE|PARKING|BUS|TRAIN", desc):
        return "Transportation"
    if re.search(r"CLOTHING|APPAREL|SHOE|FASHION", desc):
        return "Shopping"
    if re.search(r"ELECTRONIC|COMPUTER|SOFTWARE", desc):
        return "Electronics"
    return "Miscellaneous"


def match_mcc_codes(merchant_raw: str) -> Optional[Dict[str, Any]]:
    """
    Match against MCC codes (greggles/mcc-codes).
    """
    if not MCC_CODES:
        return None

    normalized = normalize_name(merchant_raw)
    best_match = None
    best_score = 0

    for mcc in MCC_CODES:
        description = mcc.get("edited_description") or mcc.get("combined_description") or ""
        score = fuzzy_score(normalized, description)
        if score > best_score:
            best_score = score
            best_match = mcc

    if best_match and best_score >= 50:
        desc = best_match.get("edited_description") or best_match.get("combined_description") or ""
        return {
            "category": mcc_description_to_category(desc),
            "subcategory": desc,
            "merchant": merchant_raw,
            "source": "mcc_codes",
            "mcc": best_match.get("mcc"),
            "confidence": "low",
            "score": best_score,
        }

    return None


def fallback_category(merchant_raw: Optional[str]) -> Dict[str, Any]:
    """
    Fallback to Miscellaneous when no matches are found.
    """
    return {
        "category": "Miscellaneous",
        "subcategory": None,
        "merchant": merchant_raw or "Unknown",
        "source": "fallback",
        "confidence": "none",
    }


# ─────────────────────────────────────────────
# 4. MAIN CATEGORIZER FUNCTION
# ─────────────────────────────────────────────

CANONICAL_CATEGORY_MAP = {
    "food": "Food & Dining",
    "food & dining": "Food & Dining",
    "food and dining": "Food & Dining",
    "dining": "Food & Dining",
    "travel": "Transportation",
    "transportation": "Transportation",
    "cab": "Transportation",
    "fuel": "Fuel",
    "bills": "Utilities & Bills",
    "utilities": "Utilities & Bills",
    "utilities & bills": "Utilities & Bills",
    "groceries": "Groceries",
    "shopping": "Shopping",
    "healthcare": "Healthcare",
    "entertainment": "Entertainment",
    "education": "Education",
    "subscriptions": "Subscriptions",
    "telecom & recharge": "Telecom & Recharge",
    "recharge": "Telecom & Recharge",
    "finance": "Finance & Insurance",
    "finance & insurance": "Finance & Insurance",
    "personal care": "Personal Care",
}


def normalize_category_name(cat: Optional[str]) -> str:
    if not cat:
        return "Other"
    key = cat.strip().lower()
    return CANONICAL_CATEGORY_MAP.get(key, cat.strip())


def categorize_transaction(parsed: Optional[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    """
    Takes parsed SMS dict and runs through the priority cascade.
    """
    if not parsed:
        return None

    merchant_raw = parsed.get("merchant_raw")
    category_result = None

    if merchant_raw:
        # Priority 1: User corrections
        category_result = match_user_corrections(merchant_raw)

        # Priority 2: Merchant DB
        if not category_result:
            category_result = match_merchant_db(merchant_raw)

        # Priority 3: MCC codes
        if not category_result:
            category_result = match_mcc_codes(merchant_raw)

    # Priority 4: Fallback
    if not category_result:
        category_result = fallback_category(merchant_raw)

    if category_result and "category" in category_result:
        category_result["category"] = normalize_category_name(category_result["category"])

    enriched = {**parsed, **category_result}
    enriched["categorized_at"] = datetime.utcnow().isoformat() + "Z"
    return enriched


def process_upi_sms(sms: str) -> Optional[Dict[str, Any]]:
    """
    Parse SMS and categorize in a single call.
    """
    parsed = parse_sms(sms)
    return categorize_transaction(parsed)


# ─────────────────────────────────────────────
# 5. USER CORRECTION (Feedback Loop)
# ─────────────────────────────────────────────

def auto_promote_to_merchant_db(
    merchant_raw: str,
    category: str,
    subcategory: Optional[str],
    display_name: Optional[str]
) -> None:
    """
    Auto-promote frequently-corrected merchants into the main merchant DB.
    """
    global MERCHANTS
    existing = any(normalize_name(m.get("name", "")) == normalize_name(merchant_raw) for m in MERCHANTS)
    if existing:
        return

    new_entry = {
        "name": display_name or merchant_raw,
        "aliases": [merchant_raw.upper()],
        "category": category,
        "subcategory": subcategory,
        "auto_promoted": True,
        "promoted_at": datetime.utcnow().isoformat() + "Z",
    }

    MERCHANTS.append(new_entry)
    print(f"[PROMOTED] Auto-promoted \"{merchant_raw}\" to merchant DB as {category}")

    try:
        if os.path.exists(MERCHANTS_PATH):
            with open(MERCHANTS_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
            data.setdefault("merchants", []).append(new_entry)
            with open(MERCHANTS_PATH, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=4)
    except Exception as err:
        print(f"Error updating merchants.json: {err}")


def save_user_correction(
    merchant_raw: str,
    new_category: str,
    subcategory: Optional[str] = None,
    display_name: Optional[str] = None
) -> None:
    """
    Save a user's manual re-categorization.
    """
    if not merchant_raw or not new_category:
        return

    key = normalize_name(merchant_raw)
    existing = USER_CORRECTIONS.get(key, {"count": 0})

    USER_CORRECTIONS[key] = {
        "merchant_raw": merchant_raw,
        "merchant_display": display_name or merchant_raw,
        "category": new_category,
        "subcategory": subcategory,
        "count": existing.get("count", 0) + 1,
        "last_updated": datetime.utcnow().isoformat() + "Z",
    }

    try:
        with open(CORRECTIONS_PATH, "w", encoding="utf-8") as f:
            json.dump(USER_CORRECTIONS, f, indent=2)
        print(f"[SAVED] Saved correction: \"{merchant_raw}\" -> {new_category}")

        if USER_CORRECTIONS[key]["count"] >= 3:
            auto_promote_to_merchant_db(merchant_raw, new_category, subcategory, display_name)
    except Exception as err:
        print(f"Error saving correction: {err}")


# ─────────────────────────────────────────────
# 6. BATCH PROCESSING
# ─────────────────────────────────────────────

def process_batch(sms_list: List[str]) -> List[Dict[str, Any]]:
    """
    Process a batch of SMS messages.
    """
    results = [process_upi_sms(sms) for sms in sms_list]
    return [r for r in results if r is not None]


# ─────────────────────────────────────────────
# 7. DEMO / TEST
# ─────────────────────────────────────────────

def run_demo() -> None:
    """
    Run a diagnostic demo to test parsing and categorization.
    """
    print("\n" + "=" * 50)
    print("       UPI SMS Transaction Categorizer - Demo")
    print("=" * 50 + "\n")

    test_sms_list = [
        "Rs.349 debited from SBI A/c XX1234 on 09-07-26. Info: UPI-SWIGGY-Swiggy Order. Avail Bal: Rs.12,456.78",
        "Your A/c XXXX5678 debited by Rs.1,299 on 09Jul26. UPI Ref 987654321. Info: UPI-NETFLIX-Netflix Subscription",
        "Dear Customer, Rs.500.00 has been debited from your account. Merchant: BPCL PETROL PUMP. Ref: 112233445",
        "Sent Rs 150 to ZOMATO INDIA PVT LTD via UPI on 09/07/2026. UPI Ref: 445566778",
        "INR 2500 paid to IRCTC via UPI. Txn ID: 998877665544. Your train ticket is confirmed.",
        "Rs.89 debited. Info: UPI-SPOTIFY-Monthly Plan. Ref No: 554433221",
        "Payment of Rs.45 to UNKNOWN KIRANA SHOP via UPI successful. Ref: 667788990",
        "Rs.12,000 credited to your account from HDFC SALARY. Ref: 223344556",
    ]

    print("Processing SMS messages...\n")

    for i, sms in enumerate(test_sms_list):
        result = process_upi_sms(sms)
        if result:
            print(f"[{i + 1}] SMS: \"{sms[:60]}...\"")
            print(f"     Amount   : Rs. {result.get('amount') or 'N/A'}")
            print(f"     Merchant : {result.get('merchant') or 'Unknown'}")
            print(f"     Category : {result.get('category')}" + (f" -> {result['subcategory']}" if result.get('subcategory') else ""))
            print(f"     Source   : {result.get('source')} ({result.get('confidence')} confidence)")
            print(f"     Type     : {result.get('type') or 'unknown'}")
            print()

    print("--- User Correction Demo ---")
    print("User re-categorizes \"UNKNOWN KIRANA SHOP\" -> Groceries\n")
    save_user_correction("UNKNOWN KIRANA SHOP", "Groceries", "Local Store", "Kirana Shop")

    corrected_result = process_upi_sms("Payment of Rs.45 to UNKNOWN KIRANA SHOP via UPI successful.")
    if corrected_result:
        print("Re-processed result:")
        print(f"  Category: {corrected_result.get('category')} (source: {corrected_result.get('source')})")


if __name__ == "__main__":
    run_demo()