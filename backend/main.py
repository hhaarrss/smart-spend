"""
Smart Expense Tracker API - Main Entry Point.

Initializes the FastAPI application, sets up CORS middleware,
registers all feature routers, and registers lifetime event handlers.
"""

import os
from pathlib import Path
from dotenv import load_dotenv, find_dotenv

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

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# Import routers
from routers.auth import router as auth_router
from routers.transactions import router as transactions_router
from routers.budget import router as budget_router
from routers.family import router as family_router
from routers.insights import router as insights_router

# Import database engine for startup check
from database import engine

# App Configuration
APP_ENV = os.getenv("APP_ENV", "development")
APP_PORT = int(os.getenv("APP_PORT", "8000"))

# Create FastAPI Instance
app = FastAPI(
    title="Smart Expense Tracker API",
    description=(
        "FastAPI Backend with async/await, SQLAlchemy, PostgreSQL, "
        "JWT Authentication, Redis + Celery workers, and Alembic migrations."
    ),
    version="1.0.0",
    docs_url="/docs" if APP_ENV == "development" else None,
    redoc_url="/redoc" if APP_ENV == "development" else None,
)

# Set up CORS middleware
ALLOWED_ORIGINS = [
    "http://localhost:5173",
    "http://localhost:3000",
    "http://127.0.0.1:5173",
    "http://127.0.0.1:3000",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Global exception handler to ensure 500 errors return proper JSON
# (prevents bare 500s from stripping CORS headers in the browser)
from fastapi import Request
from fastapi.responses import JSONResponse
import traceback as tb

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """
    Catch-all handler for unhandled exceptions.
    Returns a JSON response so CORSMiddleware can attach headers properly.
    """
    print(f"[UNHANDLED ERROR] {request.method} {request.url}")
    tb.print_exception(type(exc), exc, exc.__traceback__)
    return JSONResponse(
        status_code=500,
        content={"detail": f"Internal server error: {str(exc)}"},
    )


# Register routers
app.include_router(auth_router)
app.include_router(transactions_router)
app.include_router(budget_router)
app.include_router(family_router)
app.include_router(insights_router)


@app.on_event("startup")
async def on_startup() -> None:
    print("Initializing Smart Expense Tracker Backend...")
    try:
        from sqlalchemy import text
        async with engine.connect() as conn:
            await conn.execute(text("SELECT 1"))
        print("Database connection successfully established.")
    except Exception as e:
        print(f"Warning: Database connection could not be established during startup. Error: {e}")

@app.get("/", tags=["Health"])
async def root() -> dict:
    """
    Health check endpoint returning system status and current environment.

    Returns:
        dict: Standard system status message.
    """
    return {
        "status": "healthy",
        "app": "Smart Expense Tracker API",
        "version": "1.0.0",
        "environment": APP_ENV,
    }
