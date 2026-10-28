"""点赞数聚合口径（docs/schema/dish_rating.md / docs/schema/dish.md）。

口径：一人一菜一行；``dish.like_count`` = ``COUNT(liked = 1)``；
取消点赞置 ``liked = 0`` 保留行（再次点赞复用同一行）。
"""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from app.models import Base, Dish, DishRating
from app.student.dish import repo


@pytest.fixture
def session() -> Iterator[Session]:
    """内存库：主键显式赋值（SQLite 不为 BIGINT 主键自增）。"""
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as s:
        yield s


def _dish(session: Session, dish_id: int = 1) -> Dish:
    dish = Dish(id=dish_id, stall_id=1, name="测试菜")
    session.add(dish)
    session.flush()
    return dish


def test_首次点赞_计数加一(session: Session) -> None:
    dish = _dish(session)

    liked, count = repo.toggle_like(session, student_id=1, dish_id=1)

    assert liked is True
    assert count == 1
    assert dish.like_count == 1


def test_再次点击取消_保留行且计数减一(session: Session) -> None:
    dish = _dish(session)
    repo.toggle_like(session, student_id=1, dish_id=1)

    liked, count = repo.toggle_like(session, student_id=1, dish_id=1)

    assert liked is False
    assert count == 0
    assert dish.like_count == 0
    # 取消 = 置 liked=0 保留行（一人一菜一行）
    row = session.scalar(select(DishRating))
    assert row is not None and row.liked is False


def test_多人点赞_按人计数(session: Session) -> None:
    dish = _dish(session)

    repo.toggle_like(session, student_id=1, dish_id=1)
    _, count = repo.toggle_like(session, student_id=2, dish_id=1)

    assert count == 2
    assert dish.like_count == 2


def test_取消后再赞_恢复计数(session: Session) -> None:
    dish = _dish(session)
    repo.toggle_like(session, student_id=1, dish_id=1)
    repo.toggle_like(session, student_id=1, dish_id=1)  # 取消

    liked, count = repo.toggle_like(session, student_id=1, dish_id=1)

    assert liked is True
    assert count == 1
    assert dish.like_count == 1
    assert session.scalar(select(DishRating)) is not None
