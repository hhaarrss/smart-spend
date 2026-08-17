"""
Backfill script to recompute hash_fingerprint for existing transactions using unified formula:
f"{user_id}:{amount:.2f}:{date.date().isoformat()}:{account_last4 or 'unknown'}"
"""

import sys
import os
import argparse
import asyncio
from typing import List, Tuple

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from database import AsyncSessionLocal
from models.transaction import Transaction
from utils.fingerprint import generate_fingerprint
from sqlalchemy import select


async def run_backfill(commit: bool = False) -> Tuple[int, int, int]:
    """
    Recomputes hash_fingerprint for all transactions in the database.

    Args:
        commit (bool): If True, commits changes to the database. If False, performs dry-run.

    Returns:
        Tuple[int, int, int]: (total_rows, updated_rows, unchanged_rows)
    """
    async with AsyncSessionLocal() as db:
        query = select(Transaction)
        res = await db.execute(query)
        transactions = res.scalars().all()

        total_rows = len(transactions)
        updated_rows = 0
        unchanged_rows = 0

        # Map to detect potential duplicates under new formula
        new_fp_map = {}
        conflicts = []

        for tx in transactions:
            new_fp = generate_fingerprint(
                user_id=tx.user_id,
                amount=float(tx.amount),
                date_val=tx.date,
                account_last4=tx.account_last4
            )

            if new_fp in new_fp_map:
                conflicts.append((tx.id, new_fp_map[new_fp], new_fp))
                if commit:
                    # Remove duplicate transaction row to maintain database uniqueness
                    await db.delete(tx)
                    updated_rows += 1
                    continue
            else:
                new_fp_map[new_fp] = tx.id

            if tx.hash_fingerprint != new_fp:
                updated_rows += 1
                if commit:
                    tx.hash_fingerprint = new_fp
            else:
                unchanged_rows += 1

        if commit and updated_rows > 0:
            await db.commit()

        return total_rows, updated_rows, unchanged_rows, len(conflicts)


def main():
    parser = argparse.ArgumentParser(description="Backfill transaction hash fingerprints")
    parser.add_argument("--commit", action="store_true", help="Apply changes to the database (default: dry-run)")
    args = parser.parse_args()

    mode_str = "COMMIT MODE" if args.commit else "DRY-RUN MODE (staging report)"
    print(f"=== Transaction Fingerprint Backfill [{mode_str}] ===")

    total, updated, unchanged, conflicts = asyncio.run(run_backfill(commit=args.commit))

    print(f"Total transactions scanned: {total}")
    print(f"Fingerprints to change:   {updated}")
    print(f"Fingerprints unchanged:   {unchanged}")
    print(f"Potential collisions:     {conflicts}")
    if not args.commit:
        print("\nNote: Run with --commit to apply changes to the live database after approval.")


if __name__ == "__main__":
    main()
