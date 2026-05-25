"""
Pydantic schemas for FamilyGroup registration and membership.
"""

from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, ConfigDict, Field


class FamilyGroupBase(BaseModel):
    """Base schema for FamilyGroup metadata."""

    name: str = Field(..., max_length=100, description="The name of the family group.")


class FamilyGroupCreate(FamilyGroupBase):
    """Schema for creating a family group."""

    pass


class FamilyGroupResponse(FamilyGroupBase):
    """Schema representing family groups in API responses."""

    id: int
    admin_user_id: Optional[int] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class FamilyJoin(BaseModel):
    """Schema for joining an existing family group."""

    family_id: int = Field(..., description="ID of the family group to join.")
