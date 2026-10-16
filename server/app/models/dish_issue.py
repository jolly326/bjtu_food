"""菜品纠错（docs/schema/dish_issue.md）——只含选项；提交行永不删除。

🔴 无 updated_at；唯一可变列是 handled_at（NULL = 未处理）。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Index, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DishIssue(Base):
    __tablename__ = "dish_issue"
    __table_args__ = (
        Index("idx_issue_handled_created", "handled_at", "created_at"),
        Index("idx_issue_dish", "dish_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    dish_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("dish.id", ondelete="CASCADE"), nullable=False)
    user_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("user.id"), nullable=False)
    option_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("dish_issue_option.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    handled_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
