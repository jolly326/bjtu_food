"""菜品（docs/schema/dish.md）——like_count 是物化缓存列（点赞数）。

🔴 ``stall_id`` **无外键**（档口有独立维护语义：删档口前必须清空菜品）。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Index, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Dish(Base):
    __tablename__ = "dish"
    __table_args__ = (
        Index("idx_dish_stall", "stall_id"),
        Index("idx_dish_status", "status"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    stall_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    name: Mapped[str] = mapped_column(String(64), nullable=False, server_default="")
    image: Mapped[str | None] = mapped_column(String(255), nullable=True)
    status: Mapped[str] = mapped_column(String(32), nullable=False, server_default="on")
    # 点赞数缓存列 = COUNT(dish_rating.liked = 1)；点赞 / 取消后同事务重算
    like_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
