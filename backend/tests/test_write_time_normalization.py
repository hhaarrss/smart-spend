"""
Test suite for Write-Time Category Normalization and Database Backfill Migration.

Verifies:
1. Every write path stores canonical category names at save time (e.g. 'Food' -> 'Food & Dining').
2. The backfill migration normalizes existing legacy-format records in the database.
"""

import unittest
from datetime import datetime, timezone
from sqlalchemy import select, delete, text
from database import AsyncSessionLocal, engine, Base
from models.user import User
from models.transaction import Transaction
from models.merchant_mapping import MerchantMapping
from schemas.transaction import (
    TransactionCreate,
    TransactionUpdate,
    CategorizeRequest,
    CorrectionRequest,
)
from routers.transactions import (
    create_transaction,
    edit_transaction,
    update_transaction_fields,
    categorize_transaction_item,
    recategorize_transaction,
    TransactionUpdateSchema,
)
from utils.backfill import run_category_backfill_async


class TestWriteTimeCategoryNormalization(unittest.IsolatedAsyncioTestCase):
    """
    Validates write-time normalization across all entry points and backfill migration.
    """

    async def asyncSetUp(self):
        """
        Creates test tables and dedicated test user before each test run.
        """
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)

        self.session = AsyncSessionLocal()

        # Clean up existing test user if present
        stmt = select(User).where(User.email == "write_norm_tester@example.com")
        res = await self.session.execute(stmt)
        user = res.scalar_one_or_none()

        if user:
            await self.session.execute(delete(MerchantMapping).where(MerchantMapping.user_id == user.id))
            await self.session.execute(delete(Transaction).where(Transaction.user_id == user.id))
            await self.session.execute(delete(User).where(User.id == user.id))
            await self.session.commit()

        # Create fresh test user
        self.user = User(
            email="write_norm_tester@example.com",
            hashed_password="testpassword",
            full_name="Write Normalization Tester",
        )
        self.session.add(self.user)
        await self.session.commit()
        await self.session.refresh(self.user)
        self.user_id = self.user.id

    async def asyncTearDown(self):
        """
        Cleans up test data and closes the session after each test run.
        """
        if hasattr(self, "user_id"):
            await self.session.execute(delete(MerchantMapping).where(MerchantMapping.user_id == self.user_id))
            await self.session.execute(delete(Transaction).where(Transaction.user_id == self.user_id))
            await self.session.execute(delete(User).where(User.id == self.user_id))
            await self.session.commit()
        await self.session.close()
        await engine.dispose()

    async def test_manual_entry_write_path_normalizes_to_food_and_dining(self):
        """
        Write Path 1: Manual entry via create_transaction stores 'Food & Dining' when given 'Food'.
        """
        tx_in = TransactionCreate(
            amount=250.0,
            type="debit",
            category="Food",
            merchant="Blinkit Food",
            bank="HDFC",
            account_last4="1234",
            date=datetime.now(timezone.utc),
            source="manual",
            notes="Dinner order",
        )
        created = await create_transaction(
            tx_in=tx_in,
            current_user=self.user,
            db=self.session,
        )
        await self.session.commit()

        # Query database directly
        res = await self.session.execute(select(Transaction).where(Transaction.id == created.id))
        stored = res.scalar_one()

        self.assertEqual(stored.category, "Food & Dining")

    async def test_update_fields_write_path_normalizes_to_food_and_dining(self):
        """
        Write Path 2: update_transaction_fields stores 'Food & Dining' when given 'Food'.
        """
        tx = Transaction(
            user_id=self.user_id,
            amount=300.0,
            type="debit",
            category="Other",
            merchant="Cafe Coffee Day",
            date=datetime.now(timezone.utc),
        )
        self.session.add(tx)
        await self.session.commit()
        await self.session.refresh(tx)

        update_body = TransactionUpdateSchema(category="Food")
        updated = await update_transaction_fields(
            transaction_id=tx.id,
            body=update_body,
            db=self.session,
            current_user=self.user,
        )

        res = await self.session.execute(select(Transaction).where(Transaction.id == tx.id))
        stored = res.scalar_one()
        self.assertEqual(stored.category, "Food & Dining")

    async def test_edit_transaction_write_path_normalizes_to_food_and_dining(self):
        """
        Write Path 3: edit_transaction stores 'Food & Dining' when given 'Food'.
        """
        tx = Transaction(
            user_id=self.user_id,
            amount=450.0,
            type="debit",
            category="Shopping",
            merchant="Grocery Store",
            date=datetime.now(timezone.utc),
        )
        self.session.add(tx)
        await self.session.commit()
        await self.session.refresh(tx)

        updates = TransactionUpdate(category="Food")
        await edit_transaction(
            transaction_id=tx.id,
            updates=updates,
            db=self.session,
            current_user=self.user,
        )

        res = await self.session.execute(select(Transaction).where(Transaction.id == tx.id))
        stored = res.scalar_one()
        self.assertEqual(stored.category, "Food & Dining")

    async def test_categorize_one_click_write_path_normalizes_to_food_and_dining(self):
        """
        Write Path 4: 1-click categorize stores 'Food & Dining' when given 'Food'.
        """
        tx = Transaction(
            user_id=self.user_id,
            amount=150.0,
            type="debit",
            category="Needs Review",
            merchant="Swiggy",
            date=datetime.now(timezone.utc),
        )
        self.session.add(tx)
        await self.session.commit()
        await self.session.refresh(tx)

        payload = CategorizeRequest(category="Food", merchant_alias="Swiggy")
        await categorize_transaction_item(
            transaction_id=tx.id,
            payload=payload,
            db=self.session,
            current_user=self.user,
        )

        res = await self.session.execute(select(Transaction).where(Transaction.id == tx.id))
        stored = res.scalar_one()
        self.assertEqual(stored.category, "Food & Dining")

    async def test_recategorize_correction_write_path_normalizes_to_food_and_dining(self):
        """
        Write Path 5: recategorize_transaction stores 'Food & Dining' for both transaction and merchant mapping.
        """
        tx = Transaction(
            user_id=self.user_id,
            amount=600.0,
            type="debit",
            category="Other",
            merchant="Zomato Restaurant",
            date=datetime.now(timezone.utc),
        )
        self.session.add(tx)
        await self.session.commit()
        await self.session.refresh(tx)

        corr = CorrectionRequest(
            merchant_raw="Zomato Restaurant",
            new_category="Food",
            display_name="Zomato",
        )
        await recategorize_transaction(
            transaction_id=tx.id,
            body=corr,
            db=self.session,
            current_user=self.user,
        )

        res = await self.session.execute(select(Transaction).where(Transaction.id == tx.id))
        stored = res.scalar_one()
        self.assertEqual(stored.category, "Food & Dining")

        # Also assert learned merchant mapping is normalized
        mm_res = await self.session.execute(
            select(MerchantMapping).where(MerchantMapping.user_id == self.user_id)
        )
        mapping = mm_res.scalar_one()
        self.assertEqual(mapping.category, "Food & Dining")

    async def test_orm_model_assignment_normalizes_to_food_and_dining(self):
        """
        Write Path 6: Direct ORM Transaction(category='Food') normalizes at write time.
        """
        tx = Transaction(
            user_id=self.user_id,
            amount=199.0,
            type="debit",
            category="Food",
            merchant="Tea Post",
            date=datetime.now(timezone.utc),
        )
        self.session.add(tx)
        await self.session.commit()
        await self.session.refresh(tx)

        self.assertEqual(tx.category, "Food & Dining")

    async def test_backfill_migration_normalizes_legacy_format_rows(self):
        """
        Backfill Test: Seed 3 legacy-format rows ('Food', 'Utilities', 'Travel') via raw SQL,
        run migration function, and assert all 3 read back canonical.
        """
        now = datetime.now(timezone.utc)
        # Insert directly using raw SQL to simulate legacy database rows created before write-time normalization
        await self.session.execute(
            text(
                "INSERT INTO transactions (user_id, amount, type, category, merchant, date, is_transfer) "
                "VALUES (:user_id, 100.0, 'debit', 'Food', 'Legacy Food Joint', :date, false)"
            ),
            {"user_id": self.user_id, "date": now},
        )
        await self.session.execute(
            text(
                "INSERT INTO transactions (user_id, amount, type, category, merchant, date, is_transfer) "
                "VALUES (:user_id, 200.0, 'debit', 'Utilities', 'Legacy Electricity Board', :date, false)"
            ),
            {"user_id": self.user_id, "date": now},
        )
        await self.session.execute(
            text(
                "INSERT INTO transactions (user_id, amount, type, category, merchant, date, is_transfer) "
                "VALUES (:user_id, 300.0, 'debit', 'Travel', 'Legacy Travel Agency', :date, false)"
            ),
            {"user_id": self.user_id, "date": now},
        )
        await self.session.commit()

        # Run the backfill migration
        rows_updated = await run_category_backfill_async(self.session)
        self.assertGreaterEqual(rows_updated, 3)

        # Query the rows back and assert canonical values
        res = await self.session.execute(
            select(Transaction)
            .where(Transaction.user_id == self.user_id)
            .order_by(Transaction.amount.asc())
        )
        transactions = res.scalars().all()

        categories = [t.category for t in transactions]
        self.assertEqual(categories, ["Food & Dining", "Utilities & Bills", "Transportation"])
