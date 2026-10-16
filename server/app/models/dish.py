"""菜品（docs/schema/dish.md）——avg_reaction / reaction_count 是物化缓存列。

🔴 ``stall_id`` **无外键**（档口有独立维护语义：删档口前必须清空菜品）。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Index, Integer, Numeric, String, func
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
    price: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    image: Mapped[str | None] = mapped_column(String(255), nullable=True)
    status: Mapped[str] = mapped_column(String(32), nullable=False, server_default="on")
    # 零评分为 NULL，区分「无评分」与「评分为 0」（后者不可能，取值域 5/3/1）
    avg_reaction: Mapped[float | None] = mapped_column(Numeric(3, 1), nullable=True)
    reaction_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
