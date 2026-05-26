"""
Smart Expense Tracker API - Main Entry Point.

Initializes the FastAPI application, sets up CORS middleware,
registers all feature routers, and registers lifetime event handlers.
"""

import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Import routers
from routers.auth import router as auth_router
from routers.transactions import router as transactions_router
from routers.budget import router as budget_router
from routers.family import router as family_router

# Import database engine for startup check
from database import engine

# App Configuration
APP_ENV = os.getenv("APP_ENV", "development")
APP_PORT = int(os.getenv("APP_PORT", "8000"))
ALLOWED_ORIGINS = ["http://localhost:5173", "http://localhost:3000"]

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
app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(auth_router)
app.include_router(transactions_router)
app.include_router(budget_router)
app.include_router(family_router)


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
