"""
Categorizer utility for transaction merchants.

Maps parsed merchant names to standard expense categories based on keyword mappings.
"""

from typing import Optional

# Mappings of keywords to categories
MERCHANT_MAPPINGS = {
    # Food
    "swiggy": "Food",
    "zomato": "Food",
    "ubereats": "Food",
    "dominos": "Food",
    "pizza hut": "Food",
    "kfc": "Food",
    "mcdonald": "Food",
    "starbucks": "Food",
    "burger king": "Food",
    "haldiram": "Food",
    "faasos": "Food",
    "behrouz": "Food",
    "chai point": "Food",
    "subway": "Food",
    
    # Travel & Transport
    "irctc": "Travel",
    "makemytrip": "Travel",
    "goibibo": "Travel",
    "uber": "Travel",
    "ola": "Travel",
    "rapido": "Travel",
    "redbus": "Travel",
    "yatra": "Travel",
    "cleartrip": "Travel",
    "easemytrip": "Travel",
    "indigo": "Travel",
    "air india": "Travel",
    "spicejet": "Travel",
    "namma yatri": "Travel",
    
    # Shopping
    "amazon": "Shopping",
    "flipkart": "Shopping",
    "myntra": "Shopping",
    "ajio": "Shopping",
    "nykaa": "Shopping",
    "meesho": "Shopping",
    "zara": "Shopping",
    "h&m": "Shopping",
    "decathlon": "Shopping",
    "tata cliq": "Shopping",
    "croma": "Shopping",
    "reliance digital": "Shopping",
    "marks & spencer": "Shopping",
    "lifestyle": "Shopping",
    
    # Utilities
    "bescom": "Utilities",
    "bses": "Utilities",
    "adani electricity": "Utilities",
    "tata power": "Utilities",
    "airtel": "Utilities",
    "jio": "Utilities",
    "vi ": "Utilities",
    "act fibernet": "Utilities",
    "hathway": "Utilities",
    "indane": "Utilities",
    "bharat gas": "Utilities",
    "hp gas": "Utilities",
    "electricity": "Utilities",
    "broadband": "Utilities",
    "billdesk": "Utilities",
    "water board": "Utilities",
    
    # Entertainment
    "netflix": "Entertainment",
    "prime video": "Entertainment",
    "primevideo": "Entertainment",
    "hotstar": "Entertainment",
    "zee5": "Entertainment",
    "sonyliv": "Entertainment",
    "bookmyshow": "Entertainment",
    "spotify": "Entertainment",
    "gaana": "Entertainment",
    "youtube premium": "Entertainment",
    
    # Healthcare
    "netmeds": "Healthcare",
    "1mg": "Healthcare",
    "apollo": "Healthcare",
    "pharmeasy": "Healthcare",
    "medplus": "Healthcare",
    "practo": "Healthcare",
    "dr lal pathlabs": "Healthcare",
    "healthkart": "Healthcare",
    
    # Education
    "coursera": "Education",
    "udemy": "Education",
    "edx": "Education",
    "byjus": "Education",
    "unacademy": "Education",
    "simplilearn": "Education",
    "udacity": "Education",
    
    # Fuel
    "indian oil": "Fuel",
    "iocl": "Fuel",
    "hpcl": "Fuel",
    "bpcl": "Fuel",
    "shell": "Fuel",
    "fuel": "Fuel",
    "petrol": "Fuel",
    "cng": "Fuel",
    
    # Groceries
    "blinkit": "Groceries",
    "zepto": "Groceries",
    "instamart": "Groceries",
    "bigbasket": "Groceries",
    "jiomart": "Groceries",
    "dmart": "Groceries",
    "spencers": "Groceries",
    "star bazaar": "Groceries",
    "more retail": "Groceries",
    "groceries": "Groceries",
    "grocery": "Groceries"
}


def categorize_merchant(merchant_name: Optional[str]) -> str:
    """
    Categorizes a transaction based on the merchant name using keyword matching.

    Args:
        merchant_name (Optional[str]): The name of the merchant from the transaction.

    Returns:
        str: The categorized group ('Food', 'Travel', 'Shopping', etc.).
             Defaults to 'Other' if no match is found.
    """
    if not merchant_name:
        return "Other"
        
    merchant_normalized = merchant_name.lower().strip()
    
    # Find matching keyword
    for keyword, category in MERCHANT_MAPPINGS.items():
        if keyword in merchant_normalized:
            return category
            
    return "Other"
