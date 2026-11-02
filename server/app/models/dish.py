"""菜品（docs/schema/dish.md）——like_count 是物化缓存列（点赞数）。

归属口径：**食堂必填、档口可选、楼层可选**。

- ``canteen_id`` 外键 → ``canteen.id``，``ON DELETE RESTRICT``：有菜品引用的食堂不可删除；
- ``stall_id`` 外键 → ``stall.id``，``ON DELETE SET NULL``：档口未知时留空；档口删除后菜品保留、档口置空。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Index, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Dish(Base):
    __tablename__ = "dish"
    __table_args__ = (
        Index("idx_dish_canteen", "canteen_id"),
        Index("idx_dish_stall", "stall_id"),
        Index("idx_dish_activate", "activate"),
        # 同一食堂下菜品名唯一（档口可空 ⇒ 旧口径「同一档口下唯一」已失效）
        Index("uq_dish_canteen_name", "canteen_id", "name", unique=True),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    canteen_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("canteen.id", ondelete="RESTRICT"), nullable=False
    )
    stall_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("stall.id", ondelete="SET NULL"), nullable=True
    )
    floor: Mapped[str | None] = mapped_column(String(8), nullable=True)
    name: Mapped[str] = mapped_column(String(64), nullable=False, server_default="")
    image: Mapped[str | None] = mapped_column(String(255), nullable=True)
    activate: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="1")
    # 点赞数缓存列 = COUNT(dish_rating.liked = TRUE)；点赞 / 取消后同事务重算
    like_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
