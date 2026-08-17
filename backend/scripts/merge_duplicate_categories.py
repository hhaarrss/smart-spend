import sys
import os
import asyncio
from sqlalchemy import text

# Add parent backend directory to sys.path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from database import AsyncSessionLocal

async def run_category_migration():
    """
    Migration script to merge legacy duplicate category names in PostgreSQL database.
    Standardizes 'Food' -> 'Food & Dining', 'Travel' -> 'Transportation', 'Bills' -> 'Utilities'.
    """
    print("[MIGRATION] Starting Database Category Merge Migration...")
    
    async with AsyncSessionLocal() as db:
        try:
            # 1. Update Transactions Table
            res_tx1 = await db.execute(text("UPDATE transactions SET category = 'Food & Dining' WHERE category IN ('Food', 'Food & Dining');"))
            res_tx2 = await db.execute(text("UPDATE transactions SET category = 'Transportation' WHERE category IN ('Travel', 'Transportation');"))
            res_tx3 = await db.execute(text("UPDATE transactions SET category = 'Utilities' WHERE category IN ('Bills', 'Utilities & Bills');"))
            
            # 2. Update Budget Limits Table
            res_b1 = await db.execute(text("UPDATE budget_limits SET category = 'Food & Dining' WHERE category = 'Food';"))
            res_b2 = await db.execute(text("UPDATE budget_limits SET category = 'Transportation' WHERE category = 'Travel';"))
            
            # 3. Update Merchant Mappings Table
            res_m1 = await db.execute(text("UPDATE merchant_mappings SET category = 'Food & Dining' WHERE category = 'Food';"))
            res_m2 = await db.execute(text("UPDATE merchant_mappings SET category = 'Transportation' WHERE category = 'Travel';"))

            await db.commit()
            print("[MIGRATION] SUCCESS! Database categories merged and normalized cleanly.")
        except Exception as e:
            await db.rollback()
            print(f"[MIGRATION ERROR] Failed to execute migration: {e}")

if __name__ == '__main__':
    asyncio.run(run_category_migration())
