"""
Router for Seeding Demonstration & Testing Data.

Allows authenticated users to trigger database re-seeding for presentation purposes.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from models.user import User
from utils.dependencies import get_current_user
from seed_data import seed_database

router = APIRouter(prefix="/seed", tags=["Demo Seeding"])


class SeedResponse(BaseModel):
    """Pydantic model for seed execution response."""

    message: str
    status: str


@router.post(
    "/reset",
    response_model=SeedResponse,
    status_code=status.HTTP_200_OK,
    summary="Reset and populate presentation seed data",
)
async def trigger_seed(
    current_user: User = Depends(get_current_user)
) -> SeedResponse:
    """
    Triggers complete database re-seeding for the demo session.

    Args:
        current_user (User): Authenticated database user object.

    Returns:
        SeedResponse: Status confirmation message.
    """
    try:
        await seed_database()
        return SeedResponse(
            message="Database successfully reset and seeded with presentation data.",
            status="success"
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Seeding failed: {str(e)}"
        )
