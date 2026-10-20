"""菜品评分（docs/schema/dish_rating.md）——**口碑真源**，一人一菜一行。

🔴 与饮食记录（``dish_record``，饮食流水）**解耦**：
* 评分 / 改分 / 清除 ⇒ 本表 upsert / 删行；**删除记忆不撤票**（删 ``dish_record`` 不动本表）
* 聚合 ``dish.avg_reaction`` / ``reaction_count`` 只扫本表（一人一菜一行 ⇒ 天然去重，无需 NOT EXISTS）
* ``reacted_at`` 来源 = DB 时钟（``func.now()``），与全库时间戳纪律一致
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, SmallInteger, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DishRating(Base):
    __tablename__ = "dish_rating"
    __table_args__ = (
        UniqueConstraint("user_id", "dish_id", name="uq_rating_user_dish"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("user.id", ondelete="CASCADE"), nullable=False)
    dish_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("dish.id", ondelete="CASCADE"), nullable=False, index=True
    )
    # 5 = 喜欢 ｜ 3 = 一般 ｜ 1 = 不喜欢（本表无 NULL —— 清除评价 = 删行）
    reaction: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    reacted_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
