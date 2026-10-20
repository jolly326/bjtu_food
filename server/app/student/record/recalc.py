"""评价分重算（docs/schema/dish.md §评价分缓存列）。

口径：
* 🔴 **一人一菜一票** —— 同一用户对同一菜品只按**最新一次有效评分**计票：
  `reacted_at` 最大者为最新，并列时取 `id` 最大者；`reaction IS NULL` 的行**不参与**
  （该行被清除评价后，自动回退到该用户上一条有效评分）
* 均分 1 位小数（ROUND(AVG(reaction), 1)）
* `reaction_count` = **投票用户数**（去重后人数，**不是记录条数**）——
  同一用户多次品尝同一菜品不重复计票
* 零评分 ⇒ ``avg_reaction = NULL``、``reaction_count = 0``
  （🔴 不得兜底成 5.0，否则新菜凭空高分并污染推荐权重）
* 🔴 必须在**写评分的同一事务**内调用，否则缓存列会停在旧值
"""

from __future__ import annotations

from decimal import Decimal

from sqlalchemy import and_, func, or_, select
from sqlalchemy.orm import Session, aliased

from app.models import Dish, DishRecord


def recalc(session: Session, dish_id: int) -> None:
    """重算指定菜品的评价分缓存列（调用方负责提交事务）。"""
    later = aliased(DishRecord)
    # 存在「同用户同菜品、评分非空、更晚（或同刻但 id 更大）」的行 ⇒ 本行不是最新票，排除
    is_superseded = (
        select(later.id)
        .where(
            later.dish_id == DishRecord.dish_id,
            later.user_id == DishRecord.user_id,
            later.reaction.is_not(None),
            or_(
                later.reacted_at > DishRecord.reacted_at,
                and_(later.reacted_at == DishRecord.reacted_at, later.id > DishRecord.id),
            ),
        )
        .exists()
    )

    row = session.execute(
        select(
            func.round(func.avg(DishRecord.reaction), 1),
            func.count(DishRecord.id),
        ).where(
            DishRecord.dish_id == dish_id,
            DishRecord.reaction.is_not(None),
            ~is_superseded,
        )
    ).one()

    avg_value: Decimal | None = row[0]
    count_value: int = int(row[1] or 0)

    dish = session.get(Dish, dish_id)
    if dish is None:
        return
    dish.avg_reaction = float(avg_value) if (count_value and avg_value is not None) else None
    dish.reaction_count = count_value
