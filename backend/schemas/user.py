"""
Pydantic schemas for User authentication and profiles.
"""

from datetime import datetime
from typing import Optional
from pydantic import BaseModel, EmailStr, ConfigDict, Field


class UserBase(BaseModel):
    """Base schema containing common user fields."""

    email: EmailStr = Field(..., description="The unique email address of the user.")
    full_name: str = Field(..., max_length=100, description="The user's full name.")


class UserCreate(UserBase):
    """Schema for registering a new user."""

    password: str = Field(..., min_length=8, description="Strong user password (min 8 characters).")


class UserResponse(BaseModel):
    """Schema representing user profiles returned in API responses."""

    id: int
    email: Optional[EmailStr] = None
    phone_number: Optional[str] = None
    full_name: str
    family_id: Optional[int] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class Token(BaseModel):
    """Schema for standard OAuth2 bearer tokens."""

    access_token: str
    token_type: str = "bearer"


class TokenData(BaseModel):
    """Schema for data extracted from decoded JWT tokens."""

    email: Optional[str] = None
    user_id: Optional[int] = None


class PhoneLoginRequest(BaseModel):
    """
    Schema for the phone+OTP login endpoint.

    id_token is a Firebase ID token, obtained client-side after the user completes
    Firebase Phone Auth (OTP verification). This backend never sees the OTP itself —
    Firebase verifies it and issues the ID token as proof.
    """

    id_token: str = Field(..., description="Firebase ID token from a phone-auth sign-in.")
