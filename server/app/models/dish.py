"""菜品本体（docs/schema/dish.md）—— 一道菜的名称 / 描述 / 图片 / 价格。

位置在 :class:`DishLocation`（一道菜可挂多个食堂 / 档口），向量在
:class:`DishEmbedding`（同一道菜可并存多模型向量）。``name`` 允许重复：
不同档口的同名菜是两道不同的菜。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Index, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Dish(Base):
    __tablename__ = "dish"
    __table_args__ = (Index("idx_dish_name", "name"),)

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(64), nullable=False, server_default="")
    # 档案富文本（名称 + 口味 + 场景 + 特点），语义匹配的输入
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    image: Mapped[str | None] = mapped_column(String(255), nullable=True)
    # 单位：分；NULL = 未录入
    price: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
