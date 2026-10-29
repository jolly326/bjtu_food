"""菜品点赞（docs/schema/dish_rating.md）——**点赞真源**，一人一菜一行。

点赞 / 取消都发生在本表；群体点赞数 = ``dish.like_count``（聚合 ``COUNT(liked = TRUE)``）。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Index, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DishRating(Base):
    __tablename__ = "dish_rating"
    __table_args__ = (
        UniqueConstraint("user_id", "dish_id", name="uq_rating_user_dish"),
        Index("ix_rating_dish", "dish_id", "liked"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("user.id", ondelete="CASCADE"), nullable=False
    )
    dish_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("dish.id", ondelete="CASCADE"), nullable=False
    )
    # TRUE = 已赞 ｜ FALSE = 已取消（保留行，聚合只数 liked = TRUE）
    liked: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="1")
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
