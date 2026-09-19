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
from schemas.user import UserCreate, UserResponse, Token, PhoneLoginRequest
from utils.auth import hash_password, verify_password, create_access_token
from utils.dependencies import get_current_user
from utils.firebase_admin_client import verify_firebase_id_token

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post(
    "/phone-login",
    response_model=Token,
    summary="Sign in (or register) with a Firebase phone+OTP token",
)
async def phone_login(
    payload: PhoneLoginRequest,
    db: AsyncSession = Depends(get_db)
) -> dict:
    """
    Primary sign-in path: the client completes phone number + OTP verification
    against Firebase directly, then exchanges the resulting Firebase ID token for
    this app's own JWT. The OTP itself is never seen by this backend — only
    Firebase's signed proof that it was verified.

    Finds the user by phone number, creating one on first sign-in.

    Args:
        payload (PhoneLoginRequest): Contains the Firebase ID token to verify.
        db (AsyncSession): The database session.

    Raises:
        HTTPException: 503 if Firebase Admin isn't configured on this deployment.
        HTTPException: 401 if the token is invalid, expired, or has no phone number.

    Returns:
        dict: Object containing this app's own access token and bearer type.
    """
    claims = verify_firebase_id_token(payload.id_token)
    phone_number = claims.get("phone_number")
    if not phone_number:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Sign-in token has no verified phone number.",
        )

    result = await db.execute(select(User).where(User.phone_number == phone_number))
    user = result.scalars().first()

    if user is None:
        user = User(
            phone_number=phone_number,
            full_name="SmartSpend User",
        )
        db.add(user)
        await db.flush()  # Populates user.id

    token_data = {"sub": phone_number, "user_id": user.id}
    access_token = create_access_token(data=token_data)

    return {"access_token": access_token, "token_type": "bearer"}


@router.post(
    "/register",
    response_model=UserResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Register a new user with email + password (deprecated)",
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
    summary="Authenticate with email + password (deprecated)",
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
