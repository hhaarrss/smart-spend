"""backfill normalize transaction and merchant mapping categories

Revision ID: 7a8b9c0d1e2f
Revises: 6f0e9d8c7b21
Create Date: 2026-09-16 16:30:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from utils.backfill import run_category_backfill_sync


# revision identifiers, used by Alembic.
revision: str = "7a8b9c0d1e2f"
down_revision: Union[str, None] = "6f0e9d8c7b21"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Normalize legacy category strings to canonical names across transactions and merchant_mappings."""
    conn = op.get_bind()
    run_category_backfill_sync(conn)


def downgrade() -> None:
    """No-op downgrade: Canonical categories remain valid."""
    pass
