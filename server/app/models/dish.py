"""菜品（docs/schema/dish.md）——like_count 是物化缓存列（点赞数）。

🔴 ``stall_id`` 外键 → ``stall.id``，``ON DELETE RESTRICT``：有菜品引用的档口不可删除（删前须清空菜品）。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Index, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Dish(Base):
    __tablename__ = "dish"
    __table_args__ = (
        Index("idx_dish_stall", "stall_id"),
        Index("idx_dish_activate", "activate"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    stall_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("stall.id", ondelete="RESTRICT"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(64), nullable=False, server_default="")
    image: Mapped[str | None] = mapped_column(String(255), nullable=True)
    activate: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="1")
    # 点赞数缓存列 = COUNT(dish_rating.liked = TRUE)；点赞 / 取消后同事务重算
    like_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
