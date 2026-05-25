"""
Pydantic schemas for BudgetLimit setup and alerts.
"""

from pydantic import BaseModel, ConfigDict, Field


class BudgetLimitBase(BaseModel):
    """Base schema for BudgetLimit validation."""

    category: str = Field(..., max_length=50, description="The category constraint applies to.")
    monthly_limit: float = Field(..., gt=0, description="Monthly spending limit threshold.")
    alert_at_percent: float = Field(
        80.0, ge=10.0, le=100.0, description="Percent of budget spent that triggers alert (10-100%)."
    )
    is_family_limit: bool = Field(
        False, description="Whether this limit applies to the whole family group."
    )


class BudgetLimitCreate(BudgetLimitBase):
    """Schema for setting/creating a budget limit."""

    pass


class BudgetLimitResponse(BudgetLimitBase):
    """Schema representing budget limits in API responses."""

    id: int
    user_id: int

    model_config = ConfigDict(from_attributes=True)
