"""菜品位置（docs/schema/dish_location.md）—— 一道菜在哪个食堂 / 档口售卖。

同一道菜可挂多个位置，价格与图文共享本体；``canteen_id`` 在 ``stall_id`` 非空时
可由档口推导，属**有意反范式**（查询免 join、且能表达「食堂级位置」）。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Index, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DishLocation(Base):
    __tablename__ = "dish_location"
    __table_args__ = (
        Index("uk_location_dish_canteen_stall", "dish_id", "canteen_id", "stall_id", unique=True),
        Index("idx_location_dish", "dish_id"),
        Index("idx_location_canteen", "canteen_id"),
        Index("idx_location_stall", "stall_id"),
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
    activate: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="1")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
