"""评价分聚合口径（docs/schema/dish_record.md §评分聚合口径 / docs/schema/dish.md）。

口径：**一人一菜一票** —— 同一用户对同一菜品只按最新一次有效评分计票，
`reaction_count` = 投票用户数（不是记录条数）。
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import datetime

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.models import Base, Dish, DishRecord
from app.student.record.recalc import recalc

T0 = datetime(2026, 10, 20, 12, 0, 0)


@pytest.fixture
def session() -> Iterator[Session]:
    """内存库：只建聚合所需的三张表，主键显式赋值（SQLite 不为 BIGINT 主键自增）。"""
    engine = create_engine("sqlite://")
    Base.metadata.create_all(
        engine,
        tables=[Base.metadata.tables[name] for name in ("user", "dish", "dish_record")],
    )
    with Session(engine) as s:
        yield s


def _dish(session: Session, dish_id: int = 1) -> Dish:
    dish = Dish(id=dish_id, stall_id=1, name="测试菜", price=1000)
    session.add(dish)
    return dish


def _record(
    session: Session,
    *,
    record_id: int,
    user_id: int,
    dish_id: int = 1,
    reaction: int | None = None,
    minute: int = 0,
) -> DishRecord:
    record = DishRecord(
        id=record_id,
        user_id=user_id,
        dish_id=dish_id,
        wanted_at=T0,
        reaction=reaction,
        reacted_at=T0.replace(minute=minute) if reaction is not None else None,
    )
    session.add(record)
    return record


def test_same_user_multiple_records_counts_once_by_latest(session: Session) -> None:
    """同一用户同菜的 3 条记录（5 → 3 → 1）只按最新一条计票：avg=1.0 / count=1。"""
    dish = _dish(session)
    _record(session, record_id=1, user_id=7, reaction=5, minute=1)
    _record(session, record_id=2, user_id=7, reaction=3, minute=2)
    _record(session, record_id=3, user_id=7, reaction=1, minute=3)

    recalc(session, dish.id)

    assert dish.reaction_count == 1
    assert dish.avg_reaction == pytest.approx(1.0)


def test_distinct_users_each_count_once(session: Session) -> None:
    """不同用户各一条 ⇒ 各自计票：5 与 3 ⇒ avg=4.0 / count=2。"""
    dish = _dish(session)
    _record(session, record_id=1, user_id=1, reaction=5, minute=1)
    _record(session, record_id=2, user_id=2, reaction=3, minute=2)

    recalc(session, dish.id)

    assert dish.reaction_count == 2
    assert dish.avg_reaction == pytest.approx(4.0)


def test_unrated_rows_are_ignored(session: Session) -> None:
    """未评分行不参与聚合。"""
    dish = _dish(session)
    _record(session, record_id=1, user_id=1, reaction=5, minute=1)
    _record(session, record_id=2, user_id=1)  # reaction = NULL
    _record(session, record_id=3, user_id=2)  # reaction = NULL

    recalc(session, dish.id)

    assert dish.reaction_count == 1
    assert dish.avg_reaction == pytest.approx(5.0)


def test_cleared_latest_falls_back_to_previous_vote(session: Session) -> None:
    """最新一条被清除（reaction = NULL）⇒ 回退到该用户上一条有效评分。"""
    dish = _dish(session)
    _record(session, record_id=1, user_id=1, reaction=5, minute=1)
    _record(session, record_id=2, user_id=1, reaction=3, minute=2)
    _record(session, record_id=3, user_id=1)  # 清除评价

    recalc(session, dish.id)

    assert dish.reaction_count == 1
    assert dish.avg_reaction == pytest.approx(3.0)


def test_no_reaction_resets_cache_to_null(session: Session) -> None:
    """零评分 ⇒ avg_reaction = NULL、reaction_count = 0（🔴 不得兜底 5.0）。"""
    dish = _dish(session)
    _record(session, record_id=1, user_id=1)

    recalc(session, dish.id)

    assert dish.reaction_count == 0
    assert dish.avg_reaction is None
