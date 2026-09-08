"""
FastAPI router for user settings and FCM token registration.
"""

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import delete
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models.budget import BudgetLimit
from models.budget_alert_log import BudgetAlertLog
from models.merchant_mapping import MerchantMapping
from models.transaction import Transaction
from models.user import User
from schemas.account import DeleteAccountRequest, DeleteAccountResponse
from utils.auth import verify_password
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


@router.delete(
    "/me",
    response_model=DeleteAccountResponse,
    status_code=status.HTTP_200_OK,
    summary="Permanently delete the authenticated account and its data",
)
async def delete_my_account(
    payload: DeleteAccountRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> DeleteAccountResponse:
    """Delete the current account after explicit confirmation and re-authentication."""
    if payload.confirmation_text.strip().upper() != "DELETE":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Type DELETE to confirm account deletion.",
        )

    # Google-only accounts may not have a local password hash. The existing
    # JWT is the available authentication factor for those accounts.
    if current_user.hashed_password:
        if not payload.password or not verify_password(
            payload.password,
            current_user.hashed_password,
        ):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Incorrect password.",
                headers={"WWW-Authenticate": "Bearer"},
            )

    user_id = current_user.id

    # Explicit child deletes make the endpoint safe even where an existing
    # deployment has not yet applied all database-level cascade constraints.
    await db.execute(delete(Transaction).where(Transaction.user_id == user_id))
    await db.execute(delete(BudgetLimit).where(BudgetLimit.user_id == user_id))
    await db.execute(delete(MerchantMapping).where(MerchantMapping.user_id == user_id))
    await db.execute(delete(BudgetAlertLog).where(BudgetAlertLog.user_id == user_id))
    await db.execute(delete(User).where(User.id == user_id))
    await db.commit()

    return DeleteAccountResponse(
        success=True,
        message="Your account and all associated data have been permanently deleted.",
        deleted_at=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    )
