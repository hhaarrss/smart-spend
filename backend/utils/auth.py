"""
Security and JWT Authentication Utility Module.

Provides functions for password hashing, password verification, and JWT creation/decoding.
"""

import os
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional
from jose import jwt, JWTError
from passlib.context import CryptContext
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# JWT configuration
SECRET_KEY = os.getenv("JWT_SECRET_KEY")
ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("JWT_EXPIRY_MINUTES", "60"))

if not SECRET_KEY:
    raise ValueError("JWT_SECRET_KEY is not set in environment variables.")

import bcrypt


def hash_password(password: str) -> str:
    """
    Hashes a raw password using the bcrypt algorithm.

    Args:
        password (str): The raw string password.

    Returns:
        str: The hashed password string.
    """
    pwd_bytes = password.encode("utf-8")
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(pwd_bytes, salt).decode("utf-8")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    Verifies a plain password against its hashed database record.

    Args:
        plain_password (str): The raw user-supplied password.
        hashed_password (str): The secure hashed password.

    Returns:
        bool: True if passwords match, False otherwise.
    """
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("utf-8"))
    except Exception:
        return False


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
    """
    Generates a secure JWT access token with user identification payload.

    Args:
        data (dict): The dictionary payload containing claims.
        expires_delta (Optional[timedelta]): Custom token expiration period.

    Returns:
        str: Encoded JWT access token.
    """
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt


def decode_access_token(token: str) -> Optional[Dict[str, Any]]:
    """
    Decodes and validates a JWT access token.

    Args:
        token (str): The encoded JWT string.

    Returns:
        Optional[Dict[str, Any]]: The token payload if valid, None if invalid or expired.
    """
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        return payload
    except JWTError:
        return None
