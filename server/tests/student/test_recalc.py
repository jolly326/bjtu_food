"""评价分聚合口径（docs/schema/dish_rating.md / docs/schema/dish.md）。

口径：**聚合真源 = ``dish_rating``（一人一菜一行）** —— 天然去重，
`reaction_count` = 投票行数（= 投票人数，不是记录条数）。
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import datetime

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.models import Base, Dish, DishRating
from app.student.record.recalc import recalc

T0 = datetime(2026, 10, 20, 12, 0, 0)


@pytest.fixture
def session() -> Iterator[Session]:
    """内存库：只建聚合所需的表，主键显式赋值（SQLite 不为 BIGINT 主键自增）。"""
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as s:
        yield s


def _dish(session: Session, dish_id: int = 1) -> Dish:
    dish = Dish(id=dish_id, stall_id=1, name="测试菜", price=1000)
    session.add(dish)
    return dish


def _rating(session: Session, rating_id: int, user_id: int, dish_id: int, reaction: int) -> None:
    session.add(
        DishRating(id=rating_id, user_id=user_id, dish_id=dish_id, reaction=reaction, reacted_at=T0)
    )


def test_各用户一票_取均值(session: Session) -> None:
    dish = _dish(session)
    _rating(session, 1, user_id=1, dish_id=1, reaction=5)
    _rating(session, 2, user_id=2, dish_id=1, reaction=3)
    session.flush()

    recalc(session, 1)

    assert dish.reaction_count == 2
    assert dish.avg_reaction == pytest.approx(4.0)


def test_同一用户仅一行_天然去重(session: Session) -> None:
    """一人一菜一行（UNIQUE）⇒ 改分即覆盖，不产生第二票。"""
    dish = _dish(session)
    _rating(session, 1, user_id=1, dish_id=1, reaction=3)
    session.flush()

    recalc(session, 1)

    assert dish.reaction_count == 1
    assert dish.avg_reaction == pytest.approx(3.0)


def test_清评价_删行_撤票语义(session: Session) -> None:
    """清除评价 = 删评分行：该用户整体撤票（不是回退到旧分）。"""
    dish = _dish(session)
    _rating(session, 1, user_id=1, dish_id=1, reaction=5)
    _rating(session, 2, user_id=2, dish_id=1, reaction=3)
    session.flush()

    session.delete(session.get(DishRating, 1))
    recalc(session, 1)

    assert dish.reaction_count == 1
    assert dish.avg_reaction == pytest.approx(3.0)


def test_零评分_空缓存(session: Session) -> None:
    """零评分 ⇒ NULL / 0（不得兜底成任何数值）。"""
    dish = _dish(session)
    session.flush()

    recalc(session, 1)

    assert dish.reaction_count == 0
    assert dish.avg_reaction is None
