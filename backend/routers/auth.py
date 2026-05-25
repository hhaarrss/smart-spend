"""
Router for Authentication and Profile Management.

Handles registration, login, and profile fetching.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select

from database import get_db
from models.user import User
from schemas.user import UserCreate, UserResponse, Token
from utils.auth import hash_password, verify_password, create_access_token
from utils.dependencies import get_current_user

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post(
    "/register",
    response_model=UserResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Register a new user",
)
async def register(
    user_in: UserCreate,
    db: AsyncSession = Depends(get_db)
) -> User:
    """
    Registers a new user by checking for duplicates, hashing the password, and committing.

    Args:
        user_in (UserCreate): Schema containing user email, password, and full name.
        db (AsyncSession): The database session.

    Raises:
        HTTPException: 400 Bad Request if the email already exists in the system.

    Returns:
        User: The newly created database User object.
    """
    # Check if user already exists
    result = await db.execute(select(User).where(User.email == user_in.email))
    existing_user = result.scalars().first()
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered",
        )

    # Hash the password and create the database object
    hashed_pwd = hash_password(user_in.password)
    new_user = User(
        email=user_in.email,
        hashed_password=hashed_pwd,
        full_name=user_in.full_name,
    )

    db.add(new_user)
    await db.flush()  # Populates new_user.id
    
    return new_user


@router.post(
    "/login",
    response_model=Token,
    summary="Authenticate user and obtain a JWT access token",
)
async def login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: AsyncSession = Depends(get_db)
) -> dict:
    """
    Authenticates a user using standard OAuth2 Password Request Form (username=email, password).
    Returns a JWT access token on success.

    Args:
        form_data (OAuth2PasswordRequestForm): FastAPI form containing credentials.
        db (AsyncSession): The database session.

    Raises:
        HTTPException: 401 Unauthorized if invalid email or password.

    Returns:
        dict: Object containing the access token and bearer type.
    """
    result = await db.execute(select(User).where(User.email == form_data.username))
    user = result.scalars().first()
    
    if not user or not verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Create JWT access token
    token_data = {"sub": user.email, "user_id": user.id}
    access_token = create_access_token(data=token_data)
    
    return {"access_token": access_token, "token_type": "bearer"}


@router.get(
    "/me",
    response_model=UserResponse,
    summary="Fetch the current authenticated user profile",
)
async def get_me(
    current_user: User = Depends(get_current_user)
) -> User:
    """
    Returns the profile metadata of the current authenticated user.

    Args:
        current_user (User): The user injected by the authentication dependency.

    Returns:
        User: The authenticated user database object.
    """
    return current_user
