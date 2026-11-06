"""用户文本反馈（docs/schema/feedback.md）——单表以 ``type`` 判别产品 / 菜品反馈。

唯一可变列是 ``handled_at``（NULL = 未处理）；正文是用户自由文本，写入前过内容安全。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Index, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Feedback(Base):
    __tablename__ = "feedback"
    __table_args__ = (
        Index("idx_feedback_type_handled_created", "type", "handled_at", "created_at"),
        Index("idx_feedback_dish", "dish_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    # product 产品反馈 ｜ dish 菜品反馈
    type: Mapped[str] = mapped_column(String(8), nullable=False)
    dish_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("dish.id", ondelete="CASCADE"), nullable=True
    )
    user_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("user.id"), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    handled_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
