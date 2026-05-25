"""
Router for Family Group Management.

Allows users to establish groups, allocate shared budgets, and join groups.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from database import get_db
from models.family import FamilyGroup
from models.user import User
from schemas.family import FamilyGroupCreate, FamilyGroupResponse, FamilyJoin
from utils.dependencies import get_current_user

router = APIRouter(prefix="/family", tags=["Family Groups"])


@router.post(
    "/",
    response_model=FamilyGroupResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a new family group",
)
async def create_family_group(
    group_in: FamilyGroupCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> FamilyGroup:
    """
    Creates a new family group. The creating user is designated as the group administrator,
    and their profile is updated to belong to the new group.

    Args:
        group_in (FamilyGroupCreate): Group name data.
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Raises:
        HTTPException: 400 Bad Request if the user is already member of another family group.

    Returns:
        FamilyGroup: The newly created FamilyGroup database object.
    """
    if current_user.family_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You are already in a family group. Leave or delete it before creating a new one."
        )

    # 1. Instantiate the family group
    new_group = FamilyGroup(
        name=group_in.name,
        admin_user_id=current_user.id,
    )
    db.add(new_group)
    await db.flush()  # Populates new_group.id

    # 2. Bind the user to the newly created family group
    current_user.family_id = new_group.id
    await db.flush()

    return new_group


@router.post(
    "/join",
    response_model=FamilyGroupResponse,
    summary="Join an existing family group",
)
async def join_family_group(
    join_in: FamilyJoin,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> FamilyGroup:
    """
    Associates the authenticated user with an existing family group by ID.

    Args:
        join_in (FamilyJoin): The target family group ID.
        current_user (User): Authenticated user.
        db (AsyncSession): Database session.

    Raises:
        HTTPException: 400 Bad Request if the user is already in a family.
        HTTPException: 404 Not Found if the target family group doesn't exist.

    Returns:
        FamilyGroup: The FamilyGroup database object that was joined.
    """
    if current_user.family_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You are already in a family group. You must leave it before joining another one."
        )

    # Look up target family group
    query = select(FamilyGroup).where(FamilyGroup.id == join_in.family_id)
    result = await db.execute(query)
    family_group = result.scalars().first()

    if not family_group:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="The requested family group does not exist."
        )

    # Link the user to the family group
    current_user.family_id = family_group.id
    await db.flush()

    return family_group
