"""删除饮食记录（已评分同样可删）—— 物理删行 + 同事务重算评价分。"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import datetime

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from app.core.errors import NotFound
from app.models import Base, Dish, DishRecord
from app.student.record import service

T0 = datetime(2026, 10, 20, 12, 0, 0)


@pytest.fixture
def session() -> Iterator[Session]:
    engine = create_engine("sqlite://")
    Base.metadata.create_all(
        engine,
        tables=[Base.metadata.tables[name] for name in ("user", "dish", "dish_record")],
    )
    with Session(engine) as s:
        yield s


def _seed(session: Session) -> Dish:
    dish = Dish(id=1, stall_id=1, name="测试菜", price=1000)
    session.add(dish)
    session.add_all(
        [
            DishRecord(id=1, user_id=1, dish_id=1, wanted_at=T0, reaction=5, reacted_at=T0),
            DishRecord(id=2, user_id=2, dish_id=1, wanted_at=T0, reaction=3, reacted_at=T0),
        ]
    )
    session.flush()
    return dish


def test_delete_rated_record_recalcs(session: Session) -> None:
    """已评分记录可删；删除后按剩余有效票重算。"""
    dish = _seed(session)

    service.cancel(session, student_id=1, record_id=1)

    assert session.get(DishRecord, 1) is None
    assert dish.reaction_count == 1
    assert dish.avg_reaction == pytest.approx(3.0)


def test_delete_last_vote_resets_cache(session: Session) -> None:
    """删光该菜全部有效票 ⇒ 缓存回到零评分（NULL / 0）。"""
    dish = _seed(session)

    service.cancel(session, student_id=1, record_id=1)
    service.cancel(session, student_id=2, record_id=2)

    assert dish.reaction_count == 0
    assert dish.avg_reaction is None


def test_delete_not_owned_is_not_found(session: Session) -> None:
    """只能删自己的记录：非本人与不存在同响应（不暴露他人记录）。"""
    _seed(session)

    with pytest.raises(NotFound):
        service.cancel(session, student_id=99, record_id=1)

    assert session.scalar(select(DishRecord).where(DishRecord.id == 1)) is not None
