"""
SQLAlchemy ORM model for FamilyGroup.
"""

from datetime import datetime
from typing import List, TYPE_CHECKING
from sqlalchemy import Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.sql import func
from database import Base

if TYPE_CHECKING:
    from models.user import User


class FamilyGroup(Base):
    """
    FamilyGroup model representing a shared budget and expense group.
    """

    __tablename__ = "family_groups"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    admin_user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    # Relationships
    # Note: Use string references to avoid circular import issues
    members: Mapped[List["User"]] = relationship(
        "User",
        back_populates="family",
        foreign_keys="[User.family_id]",
    )
    
    admin: Mapped["User"] = relationship(
        "User",
        foreign_keys=[admin_user_id],
        post_update=True,
    )
