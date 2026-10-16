"""record 域业务规则（docs/func/client/B1-品尝标记.md、B2-评分反馈.md、B4-我的食谱.md）。

关键口径：
* 一行 = 一次品尝；**一人一菜可多行**
* 🔴 评分**不可修改**；重新评分须再次去品尝（新建一行）
* 「至多一条未评分记录」由本层保证（MySQL 5.7 无部分索引）
* 取消 = 物理删行，**仅限未评分**
* 写评分后**同事务**重算 dish.avg_reaction
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from sqlalchemy.orm import Session

from app.core.errors import BadRequest, NotFound
from app.models import Dish, DishRecord
from app.student.record import repo
from app.student.record.rating import recalc

VALID_REACTIONS = frozenset({5, 3, 1})


@dataclass(slots=True)
class RecordView:
    id: int
    dish_id: int
    name: str
    image_url: str | None
    wanted_at: str
    reaction: int | None


def _to_view(record: DishRecord, dish: Dish) -> RecordView:
    from app.core.storage import to_absolute

    return RecordView(
        id=record.id,
        dish_id=record.dish_id,
        name=dish.name,
        image_url=to_absolute(dish.image),
        wanted_at=record.wanted_at.strftime("%Y-%m-%d %H:%M:%S") if record.wanted_at else "",
        reaction=record.reaction,
    )


def create_record(session: Session, *, student_id: int, dish_id: int) -> RecordView:
    """去品尝：幂等 —— 已有未评分记录则直接复用。"""
    dish = repo.get_on_sale_dish(session, dish_id=dish_id)
    if dish is None:
        raise NotFound("菜品不存在或已下架")

    existing = repo.find_unrated(session, student_id=student_id, dish_id=dish_id)
    if existing is not None:
        return _to_view(existing, dish)

    record = DishRecord(user_id=student_id, dish_id=dish_id)
    session.add(record)
    session.flush()
    return _to_view(record, dish)


def react(session: Session, *, student_id: int, record_id: int, reaction: int) -> RecordView:
    if reaction not in VALID_REACTIONS:
        raise BadRequest("reaction 只能取 5（好吃）/ 3（一般）/ 1（踩）")

    record = repo.get_owned(session, student_id=student_id, record_id=record_id)
    if record is None:
        raise NotFound("记录不存在")
    if record.reaction is not None:
        raise BadRequest("该记录已评分，不能修改；想重新评分请再次去品尝")

    dish = repo.get_dish(session, dish_id=record.dish_id)
    if dish is None:
        raise NotFound("菜品不存在")

    record.reaction = reaction
    record.reacted_at = datetime.now()
    recalc(session, record.dish_id)  # 🔴 同事务重算缓存列
    session.flush()
    return _to_view(record, dish)


def cancel(session: Session, *, student_id: int, record_id: int) -> None:
    """取消品尝：物理删除，仅限未评分记录。"""
    record = repo.get_owned(session, student_id=student_id, record_id=record_id)
    if record is None:
        raise NotFound("记录不存在")
    if record.reaction is not None:
        raise BadRequest("已评分的记录不能取消")

    session.delete(record)
    session.flush()


def list_mine(session: Session, *, student_id: int) -> list[RecordView]:
    pairs = repo.list_mine_with_dish(session, student_id=student_id)
    return [_to_view(record, dish) for record, dish in pairs]
