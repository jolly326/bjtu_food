"""评价分重算（docs/schema/dish.md §评价分缓存列）。

口径：
* 只统计 ``dish_record.reaction`` **非空**行
* 均分 1 位小数（ROUND(AVG(reaction), 1)）
* 零评分 ⇒ ``avg_reaction = NULL``、``reaction_count = 0``
  （🔴 不得兜底成 5.0，否则新菜凭空高分并污染推荐权重）
* 🔴 必须在**写评分的同一事务**内调用，否则缓存列会停在旧值
"""

from __future__ import annotations

from decimal import Decimal

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Dish, DishRecord


def recalc(session: Session, dish_id: int) -> None:
    """重算指定菜品的评价分缓存列（调用方负责提交事务）。"""
    row = session.execute(
        select(
            func.round(func.avg(DishRecord.reaction), 1),
            func.count(DishRecord.id),
        ).where(
            DishRecord.dish_id == dish_id,
            DishRecord.reaction.is_not(None),
        )
    ).one()

    avg_value: Decimal | None = row[0]
    count_value: int = int(row[1] or 0)

    dish = session.get(Dish, dish_id)
    if dish is None:
        return
    dish.avg_reaction = float(avg_value) if (count_value and avg_value is not None) else None
    dish.reaction_count = count_value
