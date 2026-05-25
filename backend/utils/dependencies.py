"""
FastAPI Request Dependencies.

Implements JWT extraction, current user authentication, and route authorization filters.
"""

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select

from database import get_db
from models.user import User
from utils.auth import decode_access_token
from schemas.user import TokenData

# Define the OAuth2 password bearer flow endpoint for token retrieval
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_db)
) -> User:
    """
    Dependency that extracts, decodes, and validates the JWT token from the Authorization header.
    Looks up the corresponding user from the database.

    Args:
        token (str): JWT bearer token automatically retrieved by FastAPI.
        db (AsyncSession): The database session dependency.

    Raises:
        HTTPException: 401 Unauthorized if the token is invalid, expired, or user not found.

    Returns:
        User: The authenticated database user object.
    """
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    
    payload = decode_access_token(token)
    if payload is None:
        raise credentials_exception
        
    email: str = payload.get("sub")
    user_id: int = payload.get("user_id")
    if email is None or user_id is None:
        raise credentials_exception
        
    token_data = TokenData(email=email, user_id=user_id)
    
    # Query database asyncly
    result = await db.execute(select(User).where(User.id == token_data.user_id))
    user = result.scalars().first()
    
    if user is None:
        raise credentials_exception
        
    return user
