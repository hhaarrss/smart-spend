"""add review_status and merchant_mappings table

Revision ID: 001_add_review_status_and_merchant_mappings
Revises: 
Create Date: 2026-08-14 12:00:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '001_add_review_status_and_merchant_mappings'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Add review_status column to transactions table if not present
    op.add_column('transactions', sa.Column('review_status', sa.String(length=50), nullable=True, server_default='reviewed'))
    op.create_index(op.f('ix_transactions_review_status'), 'transactions', ['review_status'], unique=False)

    # Create merchant_mappings table
    op.create_table(
        'merchant_mappings',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('user_id', sa.Integer(), nullable=False),
        sa.Column('merchant_key', sa.String(length=255), nullable=False),
        sa.Column('category', sa.String(length=100), nullable=False),
        sa.Column('subcategory', sa.String(length=100), nullable=True),
        sa.Column('display_name', sa.String(length=255), nullable=True),
        sa.Column('count', sa.Integer(), nullable=False, server_default='1'),
        sa.Column('last_used_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_merchant_mappings_id'), 'merchant_mappings', ['id'], unique=False)
    op.create_index(op.f('ix_merchant_mappings_user_id'), 'merchant_mappings', ['user_id'], unique=False)
    op.create_index(op.f('ix_merchant_mappings_merchant_key'), 'merchant_mappings', ['merchant_key'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_merchant_mappings_merchant_key'), table_name='merchant_mappings')
    op.drop_index(op.f('ix_merchant_mappings_user_id'), table_name='merchant_mappings')
    op.drop_index(op.f('ix_merchant_mappings_id'), table_name='merchant_mappings')
    op.drop_table('merchant_mappings')

    op.drop_index(op.f('ix_transactions_review_status'), table_name='transactions')
    op.drop_column('transactions', 'review_status')
