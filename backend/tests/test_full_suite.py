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
from schemas.transaction import TransactionCreate, CorrectionRequest
from schemas.sms import SMSIngestionRequest
from schemas.budget import BudgetLimitCreate
from utils.fingerprint import generate_fingerprint


class TestFingerprint(unittest.TestCase):
    """
    Test suite for unified SHA-256 fingerprint generation logic.
    """

    def test_fingerprint_formula_and_stability(self):
        """Test SHA-256 fingerprint creation and stability across recategorization."""
        dt = datetime(2026, 8, 17, 14, 30, 0)
        fp1 = generate_fingerprint(user_id=1, amount=450.00, date_val=dt, account_last4="1234")
        fp2 = generate_fingerprint(user_id=1, amount=450.0, date_val=dt, account_last4="1234")
        
        # Verify deterministic hash
        self.assertEqual(fp1, fp2)
        self.assertEqual(len(fp1), 64)

        # Recategorizing a transaction (changing category) must NOT change fingerprint
        # as category is not part of user_id:amount:.2f:date:account_last4 formula
        fp_food = generate_fingerprint(user_id=1, amount=450.00, date_val=dt, account_last4="1234")
        fp_recategorized = generate_fingerprint(user_id=1, amount=450.00, date_val=dt, account_last4="1234")
        self.assertEqual(fp_food, fp_recategorized)

    def test_fingerprint_fallback_unknown(self):
        """Test account_last4 fallback to 'unknown'."""
        dt = datetime(2026, 8, 17, 0, 0, 0)
        fp_none = generate_fingerprint(user_id=2, amount=100.5, date_val=dt, account_last4=None)
        fp_empty = generate_fingerprint(user_id=2, amount=100.5, date_val=dt, account_last4="")
        self.assertEqual(fp_none, fp_empty)


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

    def test_ingest_schema_rejects_raw_sms(self):
        """The structured ingest payload must refuse an SMS body."""
        with self.assertRaises(ValueError):
            SMSIngestionRequest(
                amount=450.0,
                transaction_type="debit",
                merchant_raw="Blinkit",
                bank_sender_id="AD-HDFCBK",
                account_last4="1234",
                date=datetime(2026, 5, 27, 12, 0, 0),
                upi_ref="123456789012",
                raw_sms="HDFC Bank: Rs 450 debited at Blinkit",
            )

    def test_icici_upi_debit(self):
        """Test Case 1: ICICI Bank UPI Debit."""
        sms = "ICICI Bank Acct XX1234 debited for Rs 450.00 on 27-May-26; Swiggy credited. UPI:412356789012. Call 18002662 for dispute."
        parsed = parse_sms(sms, "AD-ICICIB")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["amount"], 450.00)
        self.assertEqual(parsed["type"], "debit")
        self.assertEqual(parsed["merchant"], "Swiggy")
        self.assertEqual(parsed["bank"], "ICICI")

        enriched = categorize_transaction(parsed["merchant"])
        self.assertEqual(enriched["category"], "Food & Dining")

    def test_icici_credit_card(self):
        """Test Case 3: ICICI Credit Card usage."""
        sms = "ICICI Bank Credit Card XX9087 has been used for a transaction of Rs 1850.00 on 27-May-26 at BARBEQUE NATION. If not done by you call 18002662."
        parsed = parse_sms(sms, "AD-ICICIB")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["amount"], 1850.00)
        self.assertEqual(parsed["type"], "debit")
        self.assertEqual(parsed["merchant"], "BARBEQUE NATION")

        enriched = categorize_transaction(parsed["merchant"])
        self.assertEqual(enriched["category"], "Food & Dining")

    def test_hdfc_debit_groceries(self):
        """Test Case 6: HDFC Bank Debit Groceries."""
        sms = "HDFC Bank: Rs.1200.00 debited from A/c XX5678 on 27-05-26. Info: DMART SUPERMARKET. Avl Bal: Rs.15340.00"
        parsed = parse_sms(sms, "AD-HDFCBK")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["amount"], 1200.00)
        self.assertEqual(parsed["merchant"], "DMART SUPERMARKET")

        enriched = categorize_transaction(parsed["merchant"])
        self.assertEqual(enriched["category"], "Groceries")

    def test_axis_debit_travel(self):
        """Test Case 18: Axis Bank Debit Travel."""
        sms = "Rs.8500.00 debited from Axis Bank A/c XX9012 on 28-May-26. Info: MAKEMYTRIP. Avl Bal: Rs.21450.00"
        parsed = parse_sms(sms, "AD-AXISBK")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["amount"], 8500.00)
        self.assertEqual(parsed["merchant"], "MAKEMYTRIP")

        enriched = categorize_transaction(parsed["merchant"])
        self.assertEqual(enriched["category"], "Travel & Hotels")

    def test_categorize_parsed_sms_needs_review(self):
        """Test low confidence transaction yields Needs Review and needs_review status."""
        from routers.transactions import categorize_parsed_sms
        parsed = {"amount": 500.0, "merchant": "XYZ UNKNOWN VENDOR 999", "type": "debit", "bank": "SBI"}
        result = categorize_parsed_sms(parsed["merchant"])
        self.assertEqual(result["category"], "Needs Review")
        self.assertEqual(result["review_status"], "needs_review")

    def test_categorize_parsed_sms_high_confidence(self):
        """Test high confidence merchant matches auto_categorized status."""
        from routers.transactions import categorize_parsed_sms
        parsed = {"amount": 3499.0, "merchant": "AMAZON", "type": "debit", "bank": "HDFC"}
        result = categorize_parsed_sms(parsed["merchant"])
        self.assertEqual(result["category"], "Shopping")
        self.assertEqual(result["review_status"], "auto_categorized")

    def test_credit_salary_sms_keyword_matching(self):
        """Test Credit SMS keyword matching for Salary."""
        sms = "HDFC Bank: Rs 45000.00 credited to A/c XX9876 on 30-May-26. Info: TCS SALARY. Avl Bal Rs 52000.00"
        parsed = parse_sms(sms, "AD-HDFCBK")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["type"], "credit")
        self.assertEqual(parsed["amount"], 45000.0)

        enriched = categorize_transaction(parsed["merchant"])
        self.assertEqual(enriched["category"], "Salary")
        self.assertEqual(enriched["source"], "keyword_rules")

    def test_credit_refund_sms_keyword_matching(self):
        """Test Credit SMS keyword matching for Refund."""
        sms = "ICICI Bank: Rs 1499.00 credited to Acct XX4321 on 28-May-26. Info: AMAZON REFUND. UPI: 123456789012"
        parsed = parse_sms(sms, "AD-ICICIB")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["type"], "credit")
        self.assertEqual(parsed["amount"], 1499.0)

        enriched = categorize_transaction(parsed["merchant"])
        self.assertEqual(enriched["category"], "Refund")
        self.assertEqual(enriched["source"], "keyword_rules")

    def test_credit_cashback_sms_keyword_matching(self):
        """Test Credit SMS keyword matching for Cashback."""
        sms = "Rs.100.00 credited to A/c XX5678 on 29-May-26 towards GPAY CASHBACK. Ref 987654321"
        parsed = parse_sms(sms, "AD-SBIBNK")
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["type"], "credit")
        self.assertEqual(parsed["amount"], 100.0)

        enriched = categorize_transaction(parsed["merchant"])
        self.assertEqual(enriched["category"], "Cashback")
        self.assertEqual(enriched["source"], "keyword_rules")


class TestCategoryTypeValidation(unittest.TestCase):
    """
    Test suite for validate_category_matches_type constraint checks.
    """

    def test_validation_rejection_debit_category_on_credit_type(self):
        """Reject when a debit category is assigned to a credit transaction."""
        from utils.categories import validate_category_matches_type
        # Debit categories assigned to credit type must fail
        self.assertFalse(validate_category_matches_type("Food & Dining", "credit"))
        self.assertFalse(validate_category_matches_type("Groceries", "credit"))
        self.assertFalse(validate_category_matches_type("Shopping", "credit"))
        self.assertFalse(validate_category_matches_type("Transportation", "credit"))
        self.assertFalse(validate_category_matches_type("food", "credit"))

    def test_validation_rejection_credit_category_on_debit_type(self):
        """Reject when a credit category is assigned to a debit transaction."""
        from utils.categories import validate_category_matches_type
        # Credit categories assigned to debit type must fail
        self.assertFalse(validate_category_matches_type("Salary", "debit"))
        self.assertFalse(validate_category_matches_type("Refund", "debit"))
        self.assertFalse(validate_category_matches_type("Cashback", "debit"))
        self.assertFalse(validate_category_matches_type("Interest", "debit"))
        self.assertFalse(validate_category_matches_type("salary", "debit"))

    def test_get_categories_response_format(self):
        """Test GET /categories structure has debit and credit lists with all 8 credit categories."""
        from constants.categories import DEBIT_CATEGORIES, CREDIT_CATEGORIES
        expected_credit = [
            "Salary", "Refund", "Interest", "Bank Deposit",
            "Investment Return", "Reimbursement", "Cashback", "Other Credit"
        ]
        self.assertEqual(CREDIT_CATEGORIES, expected_credit)
        self.assertIn("Food & Dining", DEBIT_CATEGORIES)


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


class TestMoMMeaningfulBaseline(unittest.IsolatedAsyncioTestCase):
    """
    Unit test suite for MoM baseline threshold guard (MIN_MEANINGFUL_BASELINE = 100.0).
    """

    async def test_mom_change_below_threshold_returns_none(self):
        """When previous month spending is below MIN_MEANINGFUL_BASELINE (100.0), get_mom_change returns None."""
        from unittest.mock import AsyncMock, MagicMock
        from services.transaction_aggregates import get_mom_change, MIN_MEANINGFUL_BASELINE

        self.assertEqual(MIN_MEANINGFUL_BASELINE, 100.0)

        # Mock DB session
        mock_db = AsyncMock()

        # Mock current month txs: spent = 300.0
        cur_tx = MagicMock()
        cur_tx.amount = 300.0
        cur_tx.category = "Food & Dining"
        cur_res = MagicMock()
        cur_res.scalars.return_value.all.return_value = [cur_tx]

        # Mock previous month txs: spent = 50.0 (< 100.0 threshold)
        prev_tx = MagicMock()
        prev_tx.amount = 50.0
        prev_tx.category = "Food & Dining"
        prev_res = MagicMock()
        prev_res.scalars.return_value.all.return_value = [prev_tx]

        mock_db.execute.side_effect = [cur_res, prev_res]

        result = await get_mom_change(
            db=mock_db,
            user_id=1,
            year=2026,
            month=8,
            category="Food & Dining",
        )
        self.assertIsNone(result)

    async def test_mom_change_above_threshold_returns_percentage(self):
        """When previous month spending is >= MIN_MEANINGFUL_BASELINE (100.0), get_mom_change returns % change."""
        from unittest.mock import AsyncMock, MagicMock
        from services.transaction_aggregates import get_mom_change, MIN_MEANINGFUL_BASELINE

        # Mock DB session
        mock_db = AsyncMock()

        # Mock current month txs: spent = 300.0
        cur_tx = MagicMock()
        cur_tx.amount = 300.0
        cur_tx.category = "Food & Dining"
        cur_res = MagicMock()
        cur_res.scalars.return_value.all.return_value = [cur_tx]

        # Mock previous month txs: spent = 200.0 (>= 100.0 threshold)
        prev_tx = MagicMock()
        prev_tx.amount = 200.0
        prev_tx.category = "Food & Dining"
        prev_res = MagicMock()
        prev_res.scalars.return_value.all.return_value = [prev_tx]

        mock_db.execute.side_effect = [cur_res, prev_res]

        # ((300 - 200) / 200) * 100 = +50.0%
        result = await get_mom_change(
            db=mock_db,
            user_id=1,
            year=2026,
            month=8,
            category="Food & Dining",
        )
        self.assertIsNotNone(result)
        self.assertEqual(result, 50.0)


if __name__ == "__main__":
    unittest.main()
