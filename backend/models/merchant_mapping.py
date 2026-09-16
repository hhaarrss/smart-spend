"""
SQLAlchemy ORM model for user-specific Merchant Mappings (Learning Engine).
"""

from datetime import datetime
from typing import Optional, TYPE_CHECKING
from sqlalchemy import Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship, validates
from sqlalchemy.sql import func
from database import Base

if TYPE_CHECKING:
    from models.user import User


class MerchantMapping(Base):
    """
    MerchantMapping model representing learned user category re-assignments per merchant/VPA.
    """

    __tablename__ = "merchant_mappings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    merchant_key: Mapped[str] = mapped_column(String(255), nullable=False, index=True)
    category: Mapped[str] = mapped_column(String(100), nullable=False)
    subcategory: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    display_name: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    count: Mapped[int] = mapped_column(Integer, default=1, server_default="1", nullable=False)
    last_used_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    # Relationships
    user: Mapped["User"] = relationship("User", back_populates="merchant_mappings")

    @validates("category")
    def validate_category(self, key: str, value: Optional[str]) -> str:
        """
        Validates and normalizes category name at write time to ensure canonical values.
        """
        from categorizer.transaction_categorizer import normalize_category_name
        if not value:
            return "Other"
        return normalize_category_name(value)

