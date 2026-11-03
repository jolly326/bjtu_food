"""菜品本体（docs/schema/dish.md）——「是什么」与「在哪卖」分离。

``dish`` 只存名称 / 描述 / 图片 / 向量；位置与价格在 :class:`DishOffering`，
一菜多售卖点 = 1 行 ``dish`` + N 行 ``dish_offering``。``name`` 全库唯一。
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from sqlalchemy import JSON, BigInteger, Boolean, DateTime, Index, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Dish(Base):
    __tablename__ = "dish"
    __table_args__ = (
        Index("uk_dish_name", "name", unique=True),
        Index("idx_dish_activate", "activate"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(64), nullable=False, server_default="")
    # 档案富文本（名称 + 口味 + 场景 + 特点），语义匹配的输入
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    image: Mapped[str | None] = mapped_column(String(255), nullable=True)
    # description 的语义向量，保存时预计算；embedding_model 记录模型标识
    embedding: Mapped[list[float] | None] = mapped_column(JSON, nullable=True)
    embedding_model: Mapped[str | None] = mapped_column(String(32), nullable=True)
    activate: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="1")
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
