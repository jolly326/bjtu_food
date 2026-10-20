"""record 域业务规则（docs/func/client/B1-品尝标记.md、B2-评分反馈.md、B4-记忆.md）。

关键口径：
* 一行 = 一次品尝；**一人一菜可多行**
* 🔴 评分**可循环修改**（喜欢 5 → 一般 3 → 不喜欢 1 → 清除 null），每次写入即时生效
* 「至多一条未评分记录」由本层保证（MySQL 5.7 无部分索引）
* 取消 = 物理删行，**仅限未评分**
* 写评分后**同事务**重算 dish.avg_reaction
* 出参附**群体风味评级** ``flavor_level``（由缓存列实时判定，样本 < 3 条 ⇒ ``None`` 待评）
* 出参附**档口 / 食堂 / 价格**（记忆卡辅助行）
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.errors import BadRequest, NotFound
from app.core.rating import flavor_level
from app.core.storage import to_absolute
from app.models import Dish, DishRecord
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
    flavor_level: str | None
    canteen_name: str
    stall_name: str
    price: int


def _to_view(
    record: DishRecord,
    dish: Dish,
    *,
    canteen_name: str = "",
    stall_name: str = "",
) -> RecordView:
    return RecordView(
        id=record.id,
        dish_id=record.dish_id,
        name=dish.name,
        image_url=to_absolute(dish.image),
        wanted_at=record.wanted_at.strftime("%Y-%m-%d %H:%M:%S") if record.wanted_at else "",
        reaction=record.reaction,
        flavor_level=flavor_level(dish.avg_reaction, dish.reaction_count),
        canteen_name=canteen_name,
        stall_name=stall_name,
        price=dish.price,
    )


def create_record(session: Session, *, student_id: int, dish_id: int) -> RecordView:
    """去品尝：幂等 —— 已有未评分记录则直接复用。"""
    dish = repo.get_on_sale_dish(session, dish_id=dish_id)
    if dish is None:
        raise NotFound("菜品不存在或已下架")

    canteen_name, stall_name = repo.place_of(session, stall_id=dish.stall_id)

    existing = repo.find_unrated(session, student_id=student_id, dish_id=dish_id)
    if existing is not None:
        return _to_view(existing, dish, canteen_name=canteen_name, stall_name=stall_name)

    record = DishRecord(user_id=student_id, dish_id=dish_id)
    session.add(record)
    session.flush()
    return _to_view(record, dish, canteen_name=canteen_name, stall_name=stall_name)


def react(session: Session, *, student_id: int, record_id: int, reaction: int | None) -> RecordView:
    """三态评分：**循环可改**（5 → 3 → 1），传 ``None`` 表示清除评价。"""
    if reaction is not None and reaction not in VALID_REACTIONS:
        raise BadRequest("reaction 只能取 5（喜欢）/ 3（一般）/ 1（不喜欢），或 null 清除评价")

    record = repo.get_owned(session, student_id=student_id, record_id=record_id)
    if record is None:
        raise NotFound("记录不存在")

    dish = repo.get_on_sale_dish(session, dish_id=record.dish_id)
    if dish is None:
        raise NotFound("菜品不存在或已下架")

    record.reaction = reaction
    # 时间戳来源 = DB 时钟（与 wanted_at / created_at 同一基准）：不用应用本地时间，
    # 否则容器时区与库时区不一致时，同一行会出现两种时区的时间
    record.reacted_at = session.scalar(select(func.now())) if reaction is not None else None
    recalc(session, record.dish_id)  # 🔴 同事务重算缓存列
    session.flush()
    canteen_name, stall_name = repo.place_of(session, stall_id=dish.stall_id)
    return _to_view(record, dish, canteen_name=canteen_name, stall_name=stall_name)


def cancel(session: Session, *, student_id: int, record_id: int) -> None:
    """删除一条饮食记录（物理删行；已评分的同样可删）。

    🔴 已评分记录删行会改变群体评价 ⇒ 必须**同事务重算** `dish.avg_reaction` /
    `reaction_count`（一人一菜一票：删掉最新一条后自动回退到该用户上一条有效评分）。
    """
    record = repo.get_owned(session, student_id=student_id, record_id=record_id)
    if record is None:
        raise NotFound("记录不存在")

    dish_id = record.dish_id
    session.delete(record)
    session.flush()
    recalc(session, dish_id)  # 🔴 同事务重算缓存列


def list_mine(session: Session, *, student_id: int) -> list[RecordView]:
    rows = repo.list_mine_with_dish(session, student_id=student_id)
    return [
        _to_view(record, dish, canteen_name=canteen_name or "", stall_name=stall_name or "")
        for record, dish, stall_name, canteen_name in rows
    ]
