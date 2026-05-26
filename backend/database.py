"""
Database Configuration.

Sets up the async SQLAlchemy engine, session maker, and model Base.
Provides a database session dependency for FastAPI routes.
"""

import os
from pathlib import Path
from typing import AsyncGenerator
from dotenv import load_dotenv, find_dotenv
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import DeclarativeBase

def mask_url(url: str | None) -> str:
    """
    Mask sensitive user credentials (like password) in the database connection URL.

    Args:
        url (str | None): The database connection URL to mask.

    Returns:
        str: The masked database URL or a placeholder if invalid/missing.
    """
    if not url:
        return "None"
    try:
        if "@" in url:
            parts = url.split("@")
            prefix = parts[0]
            suffix = parts[1]
            if "://" in prefix:
                scheme, auth = prefix.split("://", 1)
                if ":" in auth:
                    username, _ = auth.split(":", 1)
                    return f"{scheme}://{username}:*****@{suffix}"
                return f"{scheme}://*****@{suffix}"
            return f"*****@{suffix}"
        return "*****"
    except Exception:
        return "*****"

# Find and load .env file from any directory
dotenv_path = find_dotenv(usecwd=True)
if not dotenv_path:
    # Try parent directory
    dotenv_path = str(Path(__file__).resolve().parent / '.env')
load_dotenv(dotenv_path, override=True)

DATABASE_URL = os.getenv("DATABASE_URL")

# Fallback: if DATABASE_URL is still not found, try the parent directory
if not DATABASE_URL:
    parent_dotenv_path = str(Path(__file__).resolve().parent.parent / '.env')
    if os.path.exists(parent_dotenv_path):
        dotenv_path = parent_dotenv_path
        load_dotenv(dotenv_path, override=True)
        DATABASE_URL = os.getenv("DATABASE_URL")

print(f"Debug: Loaded env from: {dotenv_path} | DATABASE_URL: {mask_url(DATABASE_URL)}")

if not DATABASE_URL:
    raise ValueError("DATABASE_URL environment variable is not set")

# Ensure the database URL uses postgresql+asyncpg
if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://", 1)

# Create SQLAlchemy async engine
engine = create_async_engine(
    DATABASE_URL,
    echo=False,  # Set to True for SQL log output in development
    pool_pre_ping=True,
)

# Async session factory
AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False,
)


class Base(DeclarativeBase):
    """Base class for all SQLAlchemy ORM models."""

    pass


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """
    Dependency generator for obtaining an asynchronous database session.

    Yields:
        AsyncSession: An active database session bound to the transaction.
    """
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()
