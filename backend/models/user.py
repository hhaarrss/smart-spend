"""
SQLAlchemy ORM model for User.
"""

from datetime import datetime
from typing import List, Optional, TYPE_CHECKING
from sqlalchemy import Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.sql import func
from database import Base

if TYPE_CHECKING:
    from models.family import FamilyGroup
    from models.transaction import Transaction
    from models.budget import BudgetLimit
    from models.merchant_mapping import MerchantMapping


class User(Base):
    """
    User model representing a registered system user.
    """

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    email: Mapped[str] = mapped_column(
        String(255), unique=True, index=True, nullable=False
    )
    hashed_password: Mapped[str] = mapped_column(String(255), nullable=False)
    full_name: Mapped[str] = mapped_column(String(100), nullable=False)
    family_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("family_groups.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    # Relationships
    family: Mapped[Optional["FamilyGroup"]] = relationship(
        "FamilyGroup",
        back_populates="members",
        foreign_keys=[family_id],
    )
    
    transactions: Mapped[List["Transaction"]] = relationship(
        "Transaction",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    
    budgets: Mapped[List["BudgetLimit"]] = relationship(
        "BudgetLimit",
        back_populates="user",
        cascade="all, delete-orphan",
    )

    merchant_mappings: Mapped[List["MerchantMapping"]] = relationship(
        "MerchantMapping",
        back_populates="user",
        cascade="all, delete-orphan",
    )
