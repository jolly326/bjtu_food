"""菜品删除（docs/func/web/A-主数据维护/A3-菜品管理.md、docs/schema/dish.md）。

管理员可物理删除菜品；菜品反馈（feedback）随外键 ``ON DELETE CASCADE`` 一并清除。
"""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session

from app.admin.dish.service import delete as delete_dish
from app.models import Base, Canteen, Dish, DishLocation, Feedback, Stall, User


@pytest.fixture
def session() -> Iterator[Session]:
    """内存库：主键显式赋值（SQLite 不为 BIGINT 主键自增）。启用外键以保证 CASCADE 生效。"""
    engine = create_engine("sqlite://")

    @event.listens_for(engine, "connect")
    def _enable_fk(dbapi_conn, _record):
        dbapi_conn.execute("PRAGMA foreign_keys=ON")

    Base.metadata.create_all(engine)
    with Session(engine) as s:
        yield s


def _seed(session: Session, *, with_feedback: bool = False) -> int:
    session.add_all(
        [
            User(id=1, openid="o1"),
            Canteen(id=1, name="学一食堂"),
            Stall(id=1, canteen_id=1, name="川味小炒"),
        ]
    )
    session.flush()
    session.add(
        Dish(
            id=9,
            name="辣子鸡",
            description="川味 重辣 下饭",
            price=1200,
        )
    )
    session.flush()
    session.add(
        DishLocation(id=1, dish_id=9, canteen_id=1, stall_id=1, activate=True)
    )
    session.flush()
    if with_feedback:
        session.add(
            Feedback(id=1, type="dish", dish_id=9, user_id=1, content="东区比西区贵")
        )
        session.flush()
    session.commit()
    return 9


def test_有反馈_允许删除并级联清除(session: Session) -> None:
    dish_id = _seed(session, with_feedback=True)
    delete_dish(session, dish_id=dish_id)
    assert session.get(Dish, dish_id) is None
    assert session.get(Feedback, 1) is None


def test_零引用_允许删除(session: Session) -> None:
    dish_id = _seed(session)
    delete_dish(session, dish_id=dish_id)
    assert session.get(Dish, dish_id) is None
