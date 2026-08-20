"""
Pydantic schemas for Transaction verification and response serialization.
"""

from datetime import datetime
from typing import Optional, Dict, List
from pydantic import BaseModel, ConfigDict, Field, field_validator


class TransactionBase(BaseModel):
    """Base schema for Transaction validation."""

    amount: float = Field(..., gt=0, description="Amount must be positive.")
    type: str = Field(..., description="Transaction type: 'debit' or 'credit'.")
    category: str = Field(..., max_length=100, description="Spending or income category.")
    merchant: Optional[str] = Field(None, max_length=255, description="Name of the merchant.")
    bank: Optional[str] = Field(None, max_length=50, description="Name of the banking institution.")
    account_last4: Optional[str] = Field(
        None, max_length=50, description="Last digits/identifier of the account number."
    )
    date: datetime = Field(..., description="The transaction occurrence date and time.")
    source: str = Field(
        "manual", description="Source of transaction registration."
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
        allowed_sources = {"sms", "aa", "manual", "merchant_db", "user_correction", "mcc_codes", "fallback"}
        if val not in allowed_sources:
            raise ValueError(f"Source must be one of {allowed_sources}")
        return val


class TransactionCreate(TransactionBase):
    """Schema for adding new transactions."""

    pass


class TransactionResponse(TransactionBase):
    """Schema representing transactions returned in API responses."""

    id: int
    user_id: int
    hash_fingerprint: Optional[str] = None
    subcategory: Optional[str] = None
    raw_sms: Optional[str] = None
    upi_ref: Optional[str] = None
    confidence: Optional[str] = None
    review_status: Optional[str] = "reviewed"
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class TransactionSummaryResponse(BaseModel):
    """Schema for transaction aggregate totals (daily, monthly, yearly)."""

    daily: Dict[str, float] = Field(default_factory=dict, description="Daily spending/income aggregates.")
    monthly: Dict[str, float] = Field(default_factory=dict, description="Monthly spending/income aggregates.")
    yearly: Dict[str, float] = Field(default_factory=dict, description="Yearly spending/income aggregates.")


class SMSRequest(BaseModel):
    """Schema for single incoming SMS parsing requests."""
    sms_text: str = Field(..., description="Raw SMS content sent from mobile client.")


class BatchSMSRequest(BaseModel):
    """Schema for batch incoming SMS parsing requests."""
    sms_list: List[str] = Field(..., description="List of raw SMS contents.")


class CorrectionRequest(BaseModel):
    """Schema for transaction re-categorization correction requests."""
    transaction_id: int = Field(..., description="Target transaction ID.")
    merchant_raw: str = Field(..., description="Raw merchant name to correct future categorizations.")
    new_category: str = Field(..., description="Corrected category name.")
    subcategory: Optional[str] = Field(None, description="Optional corrected subcategory.")
    display_name: Optional[str] = Field(None, description="Optional clean merchant name.")


class PaginatedTransactionResponse(BaseModel):
    """Schema for paginated transaction list responses."""

    transactions: List[TransactionResponse]
    total_count: int = Field(..., description="Total number of matching transactions.")
    page: int = Field(..., description="Current page number.")
    limit: int = Field(..., description="Number of items per page.")
    has_more: bool = Field(..., description="True if more pages exist.")
    total_pages: int = Field(..., description="Total number of available pages.")


class CategorySummaryItem(BaseModel):
    """Schema for a single category total item in monthly summary."""

    category: str = Field(..., description="Category name.")
    total: float = Field(..., description="Total amount spent in this category.")
    percentage: float = Field(..., description="Percentage of total monthly spend.")
    transaction_count: int = Field(..., description="Number of transactions in this category.")
    top_merchant: str = Field(..., description="Merchant with highest spend in this category.")
    budget_limit: float = Field(..., description="Configured monthly budget limit for this category.")
    budget_used_percent: float = Field(..., description="Percentage of budget limit utilized.")


class MonthlyCategorySummaryResponse(BaseModel):
    """Schema for monthly category spending summary response."""

    month: int = Field(..., ge=1, le=12, description="Target month (1-12).")
    year: int = Field(..., ge=2020, le=2030, description="Target year (2020-2030).")
    total_spent: float = Field(..., description="Total amount spent across all categories in the month.")
    categories: List[CategorySummaryItem] = Field(..., description="List of category summary items sorted by total DESC.")
    previous_month_total: float = Field(..., description="Total amount spent in the previous month.")
    month_over_month_change: float = Field(..., description="Percentage change compared to previous month.")

