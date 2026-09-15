"""
FastAPI Request Dependencies.

Implements JWT extraction, current user authentication, and route authorization filters.

DEV-ONLY AUTH STUB
==================
Setting AUTH_STUB=true in .env bypasses JWT and returns a fixed stub user (id=1).
This stub is intentionally blocked in production by a hard-fail guard:

    APP_ENV=production + AUTH_STUB=true  →  RuntimeError on startup / request

It is safe to ship this file; the guard makes accidental activation in production
impossible even if AUTH_STUB is accidentally set.
"""

import os
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select

from database import get_db
from models.user import User
from utils.auth import decode_access_token
from schemas.user import TokenData

# Define the OAuth2 password bearer flow endpoint for token retrieval
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login", auto_error=False)

# Read stub config once at import time
_AUTH_STUB_ENABLED: bool = os.getenv("AUTH_STUB", "").strip().lower() in ("true", "1", "yes")
_APP_ENV: str = os.getenv("APP_ENV", "development").strip().lower()

# Hard-fail guard: prevent AUTH_STUB from ever running in production
if _AUTH_STUB_ENABLED and _APP_ENV == "production":
    raise RuntimeError(
        "CRITICAL: AUTH_STUB=true is set but APP_ENV=production. "
        "Auth stubbing is a dev-only feature and must never run in production. "
        "Unset AUTH_STUB or correct APP_ENV before deploying."
    )

# Stub user constant — matches seed user created in on_startup()
_STUB_USER_ID: int = 1


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_db)
) -> User:
    """
    Dependency that extracts, decodes, and validates the JWT token from the Authorization header.
    Looks up the corresponding user from the database.

    Dev-only behaviour when AUTH_STUB=true (blocked in production by module-level guard):
    - Bypasses JWT entirely.
    - Returns the user with id=1 from the database.
    - If id=1 does not exist, raises 503 with a helpful message.

    Args:
        token (str): JWT bearer token automatically retrieved by FastAPI (may be None in stub mode).
        db (AsyncSession): The database session dependency.

    Raises:
        HTTPException: 401 Unauthorized if the token is invalid, expired, or user not found.
        HTTPException: 503 Service Unavailable if stub user (id=1) is missing from the database.

    Returns:
        User: The authenticated database user object.
    """
    if _AUTH_STUB_ENABLED:
        # DEV STUB PATH — only reachable when APP_ENV != "production" (enforced above)
        result = await db.execute(select(User).where(User.id == _STUB_USER_ID))
        stub_user = result.scalars().first()
        if stub_user is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=(
                    f"AUTH_STUB is active but stub user (id={_STUB_USER_ID}) was not found. "
                    "Run the seed script or ensure on_startup has created the default user."
                ),
            )
        return stub_user

    # PRODUCTION / NORMAL PATH — full JWT validation
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
