"""
Pydantic schemas for Transaction verification and response serialization.
"""

from datetime import datetime
from typing import Optional, Dict
from pydantic import BaseModel, ConfigDict, Field, field_validator


class TransactionBase(BaseModel):
    """Base schema for Transaction validation."""

    amount: float = Field(..., gt=0, description="Amount must be positive.")
    type: str = Field(..., description="Transaction type: 'debit' or 'credit'.")
    category: str = Field(..., max_length=50, description="Spending or income category.")
    merchant: Optional[str] = Field(None, max_length=100, description="Name of the merchant.")
    bank: Optional[str] = Field(None, max_length=50, description="Name of the banking institution.")
    account_last4: Optional[str] = Field(
        None, min_length=4, max_length=4, description="Last 4 digits of the account number."
    )
    date: datetime = Field(..., description="The transaction occurrence date and time.")
    source: str = Field(
        "manual", description="Source of transaction registration: 'sms', 'aa', or 'manual'."
    )

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        """Validates that transaction type is either credit or debit."""
        val = v.lower()
        if val not in ("credit", "debit"):
            raise ValueError("Type must be either 'credit' or 'debit'")
        return val

    @field_validator("source")
    @classmethod
    def validate_source(cls, v: str) -> str:
        """Validates that source is one of the allowed platforms."""
        val = v.lower()
        if val not in ("sms", "aa", "manual"):
            raise ValueError("Source must be one of 'sms', 'aa', or 'manual'")
        return val


class TransactionCreate(TransactionBase):
    """Schema for adding new transactions."""

    pass


class TransactionResponse(TransactionBase):
    """Schema representing transactions returned in API responses."""

    id: int
    user_id: int
    hash_fingerprint: Optional[str] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class TransactionSummaryResponse(BaseModel):
    """Schema for transaction aggregate totals (daily, monthly, yearly)."""

    daily: Dict[str, float] = Field(default_factory=dict, description="Daily spending/income aggregates.")
    monthly: Dict[str, float] = Field(default_factory=dict, description="Monthly spending/income aggregates.")
    yearly: Dict[str, float] = Field(default_factory=dict, description="Yearly spending/income aggregates.")
