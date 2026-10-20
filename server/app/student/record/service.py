"""record 域业务规则（docs/func/client/B1-品尝标记.md、B2-评分反馈.md、B4-记忆.md）。

关键口径：
* 一行 = 一次品尝；**一人一菜可多行**
* 🔴 **评分真源 = ``dish_rating``（一人一菜一行），与流水解耦**：评分 / 改分 / 清除写评分表；
  **删除记忆不撤票**（不动评分）
* 🔴 评分**可循环修改**（喜欢 5 → 一般 3 → 不喜欢 1 → 清除 = 删评分行），每次写入即时生效
* 「至多一条未评分记录」由本层保证（MySQL 5.7 无部分索引）
* 写评分后**同事务**重算 dish.avg_reaction（聚合只扫评分表）
* 出参附**档口 / 食堂 / 价格**（记忆卡辅助行）与**当前评分**（读评分表）；
  🔴 **不下发群体评级**（记忆卡不展示，评级只在菜品卡 `DishCardOut`）
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import cast

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.errors import BadRequest, NotFound
from app.core.storage import to_absolute
from app.models import Dish, DishRating, DishRecord
from app.student.record import repo
from app.student.record.recalc import recalc

VALID_REACTIONS = frozenset({5, 3, 1})


@dataclass(slots=True)
class RecordView:
    id: int
    dish_id: int
    name: str
    image_url: str | None
    wanted_at: str
    reaction: int | None
    off_shelf: bool
    canteen_name: str
    stall_name: str
    price: int


def _to_view(
    record: DishRecord,
    dish: Dish,
    *,
    rating_reaction: int | None,
    canteen_name: str = "",
    stall_name: str = "",
) -> RecordView:
    return RecordView(
        id=record.id,
        dish_id=record.dish_id,
        name=dish.name,
        image_url=to_absolute(dish.image),
        wanted_at=record.wanted_at.strftime("%Y-%m-%d %H:%M:%S") if record.wanted_at else "",
        reaction=rating_reaction,
        off_shelf=dish.status == "off",
        canteen_name=canteen_name,
        stall_name=stall_name,
        price=dish.price,
    )


def create_record(session: Session, *, student_id: int, dish_id: int) -> RecordView:
    """去品尝：幂等 —— 已有未评分记录则直接复用；若此前评过该菜，出参直接带出当前评分。"""
    dish = repo.get_on_sale_dish(session, dish_id=dish_id)
    if dish is None:
        raise NotFound("菜品不存在或已下架")

    canteen_name, stall_name = repo.place_of(session, stall_id=dish.stall_id)

    existing = repo.find_unrated(session, student_id=student_id, dish_id=dish_id)
    rating = repo.rating_of(session, student_id=student_id, dish_id=dish_id)
    rating_reaction = rating.reaction if rating else None
    if existing is not None:
        return _to_view(
            existing,
            dish,
            rating_reaction=rating_reaction,
            canteen_name=canteen_name,
            stall_name=stall_name,
        )

    record = DishRecord(user_id=student_id, dish_id=dish_id)
    session.add(record)
    session.flush()
    return _to_view(
        record,
        dish,
        rating_reaction=rating_reaction,
        canteen_name=canteen_name,
        stall_name=stall_name,
    )


def react(session: Session, *, student_id: int, record_id: int, reaction: int | None) -> RecordView:
    """三态评分：**循环可改**（5 → 3 → 1），传 ``None`` 表示清除评价。

    🔴 写**评分表** ``dish_rating``（upsert / 删行），不动饮食记录 —— 流水与口碑解耦。
    """
    if reaction is not None and reaction not in VALID_REACTIONS:
        raise BadRequest("reaction 只能取 5（喜欢）/ 3（一般）/ 1（不喜欢），或 null 清除评价")

    record = repo.get_owned(session, student_id=student_id, record_id=record_id)
    if record is None:
        raise NotFound("记录不存在")

    # 已下架的菜同样可评（记忆在，口碑仍在）；只要求菜品行存在
    dish = repo.get_dish(session, dish_id=record.dish_id)
    if dish is None:
        raise NotFound("菜品不存在")

    rating = repo.rating_of(session, student_id=student_id, dish_id=record.dish_id)
    if reaction is None:
        # 清除评价 = 整体撤票（删评分行）
        if rating is not None:
            session.delete(rating)
    elif rating is None:
        session.add(DishRating(user_id=student_id, dish_id=record.dish_id, reaction=reaction))
    else:
        rating.reaction = reaction
        # 时间戳来源 = DB 时钟（与 wanted_at / created_at 同一基准）：不用应用本地时间，
        # 否则容器时区与库时区不一致时，同一行会出现两种时区的时间。
        # select(func.now()) 恒返回一行：cast 仅消解 ORM 赋值描述符的类型推断（scalar 被推为可空）
        rating.reacted_at = cast(datetime, session.scalar(select(func.now())))
    recalc(session, record.dish_id)  # 🔴 同事务重算缓存列
    session.flush()

    canteen_name, stall_name = repo.place_of(session, stall_id=dish.stall_id)
    current = reaction if reaction is not None else (rating.reaction if rating else None)
    return _to_view(
        record,
        dish,
        rating_reaction=current,
        canteen_name=canteen_name,
        stall_name=stall_name,
    )


def cancel(session: Session, *, student_id: int, record_id: int) -> None:
    """删除一条饮食记录（物理删行；未评分 / 已评分均可删）。

    🔴 **删除记忆不撤票** —— 评分真源在 ``dish_rating``，本操作**不动评分、不重算**：
    该用户的口碑贡献独立于私人流水存在。
    """
    record = repo.get_owned(session, student_id=student_id, record_id=record_id)
    if record is None:
        raise NotFound("记录不存在")

    session.delete(record)
    session.flush()


def list_mine(session: Session, *, student_id: int) -> list[RecordView]:
    rows = repo.list_mine_with_dish(session, student_id=student_id)
    ratings = repo.ratings_of(session, student_id=student_id)
    return [
        _to_view(
            record,
            dish,
            rating_reaction=ratings.get(record.dish_id),
            canteen_name=canteen_name or "",
            stall_name=stall_name or "",
        )
        for record, dish, stall_name, canteen_name in rows
    ]
