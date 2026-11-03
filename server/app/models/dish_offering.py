"""菜品售卖点（docs/schema/dish_offering.md）——「在哪卖、多少钱」的唯一真源。

一道菜可在多个食堂 / 档口售卖，各售卖点价格独立（单位：分）。
``stall_id`` 可空：学校自营 / 大食堂统一出餐等无具体档口的情况。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Index, Integer, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DishOffering(Base):
    __tablename__ = "dish_offering"
    __table_args__ = (
        Index("idx_offering_dish", "dish_id"),
        Index("idx_offering_canteen", "canteen_id"),
        Index("uq_offering_dish_canteen_stall", "dish_id", "canteen_id", "stall_id", unique=True),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    dish_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("dish.id", ondelete="CASCADE"), nullable=False
    )
    canteen_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("canteen.id", ondelete="RESTRICT"), nullable=False
    )
    stall_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("stall.id", ondelete="SET NULL"), nullable=True
    )
    # 单位：分；NULL = 未录入
    price: Mapped[int | None] = mapped_column(Integer, nullable=True)
    activate: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="1")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
