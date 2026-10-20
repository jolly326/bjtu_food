"""饮食记录（docs/schema/dish_record.md）——一行 = 一次「去品尝」+ 评分。

🔴 无 updated_at；评分可反复改写（写入 `reaction` + `reacted_at`）；未评分记录可物理删除。
🔴 一人一菜可多行（每次品尝一行）；「至多一条未评分记录」由应用层保证。
🔴 评价分聚合**一人一菜一票**（取该用户该菜最新一次有效评分），聚合索引见 `idx_record_user_dish`。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Index, SmallInteger, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DishRecord(Base):
    __tablename__ = "dish_record"
    __table_args__ = (
        Index("idx_record_user_wanted", "user_id", "wanted_at"),
        Index("idx_record_dish_reaction", "dish_id", "reaction"),
        Index("idx_record_user_dish", "user_id", "dish_id", "reacted_at"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("user.id"), nullable=False)
    dish_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("dish.id", ondelete="CASCADE"), nullable=False)
    wanted_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    # 5 = 喜欢 ｜ 3 = 一般 ｜ 1 = 不喜欢 ｜ NULL = 尚未评分
    reaction: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    reacted_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
