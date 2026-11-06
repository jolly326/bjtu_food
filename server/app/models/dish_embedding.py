"""菜品语义向量（docs/schema/dish_embedding.md）—— 一个菜品可并存多模型向量。

由 ``description`` 派生，``(dish_id, model)`` 联合主键；检索端只信任与当前模型一致的行。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import JSON, BigInteger, DateTime, ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DishEmbedding(Base):
    __tablename__ = "dish_embedding"

    dish_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("dish.id", ondelete="CASCADE"), primary_key=True
    )
    model: Mapped[str] = mapped_column(String(32), primary_key=True)
    vector: Mapped[list[float]] = mapped_column(JSON, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
