"""Schemas for account lifecycle operations."""

from typing import Optional

from pydantic import BaseModel, Field


class DeleteAccountRequest(BaseModel):
    """Confirmation data required before permanently deleting an account."""

    password: Optional[str] = Field(
        None,
        description="Current password for password-based accounts.",
    )
    confirmation_text: str = Field(
        ...,
        min_length=1,
        max_length=20,
        description="Must be DELETE, case-insensitively.",
    )


class DeleteAccountResponse(BaseModel):
    """Result returned after the account and its owned data are deleted."""

    success: bool
    message: str
    deleted_at: str
