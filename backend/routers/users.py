"""
FastAPI router for user settings and FCM token registration.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models.user import User
from utils.dependencies import get_current_user


router = APIRouter(prefix="/users", tags=["Users"])


class FCMTokenRequest(BaseModel):
    """Payload for saving FCM push notification token."""
    fcm_token: str = Field(..., max_length=500, description="Firebase Cloud Messaging device token.")


@router.post(
    "/fcm-token",
    status_code=status.HTTP_200_OK,
    summary="Register FCM push notification device token"
)
async def update_fcm_token(
    payload: FCMTokenRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Saves or updates the user's FCM device token for push notifications.
    """
    current_user.fcm_token = payload.fcm_token
    await db.commit()
    return {"message": "FCM device token registered successfully"}
