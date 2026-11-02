"""菜品删除（docs/func/web/A3-菜品管理.md、docs/schema/dish.md）。

管理员可物理删除菜品；存在学生纠错（dish_issue）/ 点赞（dish_rating）时仍可删除，
引用项随外键 ``ondelete="CASCADE"`` 一并清除。
"""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session

from app.admin.dish.service import delete as delete_dish
from app.models import (
    Base,
    Canteen,
    Dish,
    DishIssue,
    DishIssueOption,
    DishRating,
    Stall,
    User,
)


@pytest.fixture
def session() -> Iterator[Session]:
    """内存库：建引用链所需各表，主键显式赋值（SQLite 不为 BIGINT 主键自增）。启用外键以保证 CASCADE 生效。"""
    engine = create_engine("sqlite://")

    @event.listens_for(engine, "connect")
    def _enable_fk(dbapi_conn, _record):
        dbapi_conn.execute("PRAGMA foreign_keys=ON")

    Base.metadata.create_all(engine)
    with Session(engine) as s:
        yield s


def _seed(
    session: Session,
    *,
    with_issue: bool = False,
    with_rating: bool = False,
) -> int:
    session.add_all(
        [
            User(id=1, openid="o1"),
            Canteen(id=1, name="学一食堂"),
            Stall(id=1, canteen_id=1, name="川味小炒"),
            Dish(id=9, canteen_id=1, stall_id=1, name="辣子鸡", activate=True),
        ]
    )
    if with_issue:
        session.add(DishIssueOption(id=2))
        session.add(DishIssue(id=1, user_id=1, dish_id=9, option_id=2))
    if with_rating:
        session.add(DishRating(id=1, user_id=1, dish_id=9, liked=True))
    session.commit()
    return 9


def test_有纠错_允许删除并级联清除(session: Session) -> None:
    dish_id = _seed(session, with_issue=True)
    delete_dish(session, dish_id=dish_id)
    assert session.get(Dish, dish_id) is None
    assert session.get(DishIssue, 1) is None


def test_有点赞_允许删除并级联清除(session: Session) -> None:
    dish_id = _seed(session, with_rating=True)
    delete_dish(session, dish_id=dish_id)
    assert session.get(Dish, dish_id) is None
    assert session.get(DishRating, 1) is None


def test_零引用_允许删除(session: Session) -> None:
    dish_id = _seed(session)
    delete_dish(session, dish_id=dish_id)
    assert session.get(Dish, dish_id) is None
