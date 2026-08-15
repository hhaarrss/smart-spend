"""
Comprehensive Unit & Integration Test Suite for Smart Expense Tracker.

Tests SMS parsing, categorization engine, password security,
and transaction schema validations.
"""

import unittest
from datetime import datetime
from utils.sms_parser import parse_sms, parse_sms_date, clean_amount
from categorizer.transaction_categorizer import categorize_transaction
from utils.auth import hash_password, verify_password, create_access_token
from schemas.transaction import TransactionCreate, SMSRequest, CorrectionRequest
from schemas.budget import BudgetLimitCreate


class TestSMSParserAndCategorizer(unittest.TestCase):
    """
    Test suite for SMS parser regex rules and transaction categorization engine.
    """

    def test_clean_amount(self):
        """Test currency amount string cleaning."""
        self.assertEqual(clean_amount("Rs. 1,250.50"), 1250.50)
        self.assertEqual(clean_amount("INR 500"), 500.0)
        self.assertEqual(clean_amount("450.00"), 450.00)

    def test_parse_sms_date(self):
        """Test date string parsing."""
        dt = parse_sms_date("27-May-26")
        self.assertEqual(dt.day, 27)
        self.assertEqual(dt.month, 5)
        self.assertEqual(dt.year, 2026)

    def test_icici_upi_debit(self):
        """Test Case 1: ICICI Bank UPI Debit."""
        sms = "ICICI Bank Acct XX1234 debited for Rs 450.00 on 27-May-26; Swiggy credited. UPI:412356789012. Call 18002662 for dispute."
        parsed = parse_sms(sms, "AD-ICICIB")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["amount"], 450.00)
        self.assertEqual(parsed["type"], "debit")
        self.assertEqual(parsed["merchant"], "Swiggy")
        self.assertEqual(parsed["bank"], "ICICI")

        enriched = categorize_transaction({**parsed, "raw": sms, "merchant_raw": parsed["merchant"]})
        self.assertEqual(enriched["category"], "Food & Dining")

    def test_icici_credit_card(self):
        """Test Case 3: ICICI Credit Card usage."""
        sms = "ICICI Bank Credit Card XX9087 has been used for a transaction of Rs 1850.00 on 27-May-26 at BARBEQUE NATION. If not done by you call 18002662."
        parsed = parse_sms(sms, "AD-ICICIB")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["amount"], 1850.00)
        self.assertEqual(parsed["type"], "debit")
        self.assertEqual(parsed["merchant"], "BARBEQUE NATION")

        enriched = categorize_transaction({**parsed, "raw": sms, "merchant_raw": parsed["merchant"]})
        self.assertEqual(enriched["category"], "Food & Dining")

    def test_hdfc_debit_groceries(self):
        """Test Case 6: HDFC Bank Debit Groceries."""
        sms = "HDFC Bank: Rs.1200.00 debited from A/c XX5678 on 27-05-26. Info: DMART SUPERMARKET. Avl Bal: Rs.15340.00"
        parsed = parse_sms(sms, "AD-HDFCBK")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["amount"], 1200.00)
        self.assertEqual(parsed["merchant"], "DMART SUPERMARKET")

        enriched = categorize_transaction({**parsed, "raw": sms, "merchant_raw": parsed["merchant"]})
        self.assertEqual(enriched["category"], "Groceries")

    def test_axis_debit_travel(self):
        """Test Case 18: Axis Bank Debit Travel."""
        sms = "Rs.8500.00 debited from Axis Bank A/c XX9012 on 28-May-26. Info: MAKEMYTRIP. Avl Bal: Rs.21450.00"
        parsed = parse_sms(sms, "AD-AXISBK")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["amount"], 8500.00)
        self.assertEqual(parsed["merchant"], "MAKEMYTRIP")

        enriched = categorize_transaction({**parsed, "raw": sms, "merchant_raw": parsed["merchant"]})
        self.assertEqual(enriched["category"], "Travel & Hotels")

    def test_categorize_parsed_sms_needs_review(self):
        """Test low confidence transaction yields Needs Review and needs_review status."""
        from routers.transactions import categorize_parsed_sms
        parsed = {"amount": 500.0, "merchant": "XYZ UNKNOWN VENDOR 999", "type": "debit", "bank": "SBI"}
        result = categorize_parsed_sms(parsed, "Rs 500 paid to XYZ UNKNOWN VENDOR 999")
        self.assertEqual(result["category"], "Needs Review")
        self.assertEqual(result["review_status"], "needs_review")

    def test_categorize_parsed_sms_high_confidence(self):
        """Test high confidence merchant matches auto_categorized status."""
        from routers.transactions import categorize_parsed_sms
        parsed = {"amount": 3499.0, "merchant": "AMAZON", "type": "debit", "bank": "HDFC"}
        result = categorize_parsed_sms(parsed, "HDFC Bank: Rs.3499.00 debited... Info: AMAZON")
        self.assertEqual(result["category"], "Shopping")
        self.assertEqual(result["review_status"], "auto_categorized")


class TestSecurityUtilities(unittest.TestCase):
    """
    Test suite for password hashing and JWT token creation.
    """

    def test_password_hashing(self):
        """Test password hash generation and verification."""
        password = "SecurePassword@123"
        hashed = hash_password(password)
        self.assertTrue(verify_password(password, hashed))
        self.assertFalse(verify_password("WrongPassword", hashed))

    def test_jwt_token_creation(self):
        """Test JWT token encoding."""
        token = create_access_token(data={"sub": "user@example.com"})
        self.assertIsInstance(token, str)
        self.assertTrue(len(token) > 20)


class TestPydanticSchemas(unittest.TestCase):
    """
    Test suite for API Pydantic request & response schemas.
    """

    def test_transaction_create_schema(self):
        """Test manual transaction creation schema."""
        tx_data = {
            "amount": 450.00,
            "type": "debit",
            "category": "Food",
            "merchant": "Swiggy",
            "bank": "HDFC",
            "account_last4": "1234",
            "date": datetime.now(),
            "source": "manual"
        }
        schema = TransactionCreate(**tx_data)
        self.assertEqual(schema.amount, 450.00)
        self.assertEqual(schema.category, "Food")

    def test_budget_limit_create_schema(self):
        """Test budget limit schema creation."""
        budget_data = {
            "category": "Food",
            "monthly_limit": 5000.00,
            "alert_at_percent": 80.0,
            "is_family_limit": False
        }
        schema = BudgetLimitCreate(**budget_data)
        self.assertEqual(schema.monthly_limit, 5000.00)
        self.assertEqual(schema.alert_at_percent, 80.0)


if __name__ == "__main__":
    unittest.main()
