"""purge raw_sms from transactions

Revision ID: 2d7a9c4e5f30
Revises: 470b6e867015
Create Date: 2026-09-13 13:30:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
import logging


# revision identifiers, used by Alembic.
revision: str = "2d7a9c4e5f30"
down_revision: Union[str, None] = "470b6e867015"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None
logger = logging.getLogger("alembic.runtime.migration")


def upgrade() -> None:
    bind = op.get_bind()
    purge_count = bind.execute(
        sa.text(
            """
            SELECT COUNT(*)
            FROM transactions
            WHERE raw_sms IS NOT NULL
              AND btrim(raw_sms) <> ''
            """
        )
    ).scalar_one()

    bind.execute(
        sa.text(
            """
            UPDATE transactions
            SET raw_sms = NULL
            WHERE raw_sms IS NOT NULL
            """
        )
    )

    message = f"[privacy-cleanup] Purged raw SMS text from {purge_count} historical transactions."
    logger.info(message)
    print(message)


def downgrade() -> None:
    message = "[privacy-cleanup] Downgrade does not restore purged raw SMS text."
    logger.info(message)
    print(message)
