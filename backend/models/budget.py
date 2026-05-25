"""
SQLAlchemy ORM model for BudgetLimit.
"""

from typing import TYPE_CHECKING
from sqlalchemy import Integer, Numeric, String, ForeignKey, Boolean, Float
from sqlalchemy.orm import Mapped, mapped_column, relationship
from database import Base

if TYPE_CHECKING:
    from models.user import User


class BudgetLimit(Base):
    """
    BudgetLimit model representing a category-based monthly spending constraint.
    """

    __tablename__ = "budget_limits"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    category: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    monthly_limit: Mapped[float] = mapped_column(Numeric(12, 2), nullable=False)
    alert_at_percent: Mapped[float] = mapped_column(
        Float, default=80.0, nullable=False
    )
    is_family_limit: Mapped[bool] = mapped_column(
        Boolean, default=False, nullable=False
    )

    # Relationships
    user: Mapped["User"] = relationship("User", back_populates="budgets")
