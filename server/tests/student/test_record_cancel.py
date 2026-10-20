"""删除饮食记录（未评分 / 已评分均可删）——🔴 **删除记忆不撤票**。

评分真源 = ``dish_rating``：删 ``dish_record`` 行**不动评分、不重算**；
该用户的口碑贡献独立于私人流水存在。
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import datetime

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from app.core.errors import NotFound
from app.models import Base, Dish, DishRating, DishRecord
from app.student.record import service

T0 = datetime(2026, 10, 20, 12, 0, 0)


@pytest.fixture
def session() -> Iterator[Session]:
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as s:
        yield s


def _seed(session: Session) -> Dish:
    dish = Dish(id=1, stall_id=1, name="测试菜", price=1000, avg_reaction=4.0, reaction_count=2)
    session.add(dish)
    session.add_all(
        [
            DishRecord(id=1, user_id=1, dish_id=1, wanted_at=T0),
            DishRecord(id=2, user_id=2, dish_id=1, wanted_at=T0),
            # 口碑真源：两位用户各一票（4.0 / 2 票）
            DishRating(id=1, user_id=1, dish_id=1, reaction=5, reacted_at=T0),
            DishRating(id=2, user_id=2, dish_id=1, reaction=3, reacted_at=T0),
        ]
    )
    session.flush()
    return dish


def test_delete_rated_record_keeps_rating(session: Session) -> None:
    """删除已评分记忆 ⇒ 记录消失、**评分行原样保留**、口碑缓存不动。"""
    dish = _seed(session)

    service.cancel(session, student_id=1, record_id=1)

    assert session.get(DishRecord, 1) is None
    rating = session.scalar(select(DishRating).where(DishRating.user_id == 1))
    assert rating is not None and rating.reaction == 5
    assert dish.reaction_count == 2
    assert dish.avg_reaction == pytest.approx(4.0)


def test_delete_not_owned_is_not_found(session: Session) -> None:
    """只能删自己的记录：非本人与不存在同响应（不暴露他人记录）。"""
    _seed(session)

    with pytest.raises(NotFound):
        service.cancel(session, student_id=99, record_id=1)

    assert session.scalar(select(DishRecord).where(DishRecord.id == 1)) is not None
