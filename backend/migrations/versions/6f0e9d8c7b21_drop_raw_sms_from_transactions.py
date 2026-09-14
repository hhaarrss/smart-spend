"""drop raw_sms from transactions

Revision ID: 6f0e9d8c7b21
Revises: 2d7a9c4e5f30
Create Date: 2026-09-13 13:15:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "6f0e9d8c7b21"
down_revision: Union[str, None] = "2d7a9c4e5f30"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.drop_column("transactions", "raw_sms")


def downgrade() -> None:
    op.add_column("transactions", sa.Column("raw_sms", sa.Text(), nullable=True))
