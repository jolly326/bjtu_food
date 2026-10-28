"""菜品删除「引用即禁删」（docs/func/web/A3-菜品管理.md、docs/schema/dish.md）。

存在学生纠错（dish_issue）⇒ `BadRequest` 引导改用「下架」；
仅当零引用才允许物理删除（CASCADE 仅作兜底，正常路径不可达）。
"""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.admin.dish.service import delete as delete_dish
from app.core.errors import BadRequest
from app.models import Base, Canteen, Dish, DishIssue, Stall, User


@pytest.fixture
def session() -> Iterator[Session]:
    """内存库：建引用链所需各表，主键显式赋值（SQLite 不为 BIGINT 主键自增）。"""
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as s:
        yield s


def _seed(session: Session, *, with_issue: bool) -> int:
    session.add_all(
        [
            User(id=1, openid="o1"),
            Canteen(id=1, name="学一食堂"),
            Stall(id=1, canteen_id=1, name="川味小炒"),
            Dish(id=9, stall_id=1, name="辣子鸡", status="on"),
        ]
    )
    if with_issue:
        session.add(DishIssue(id=1, user_id=1, dish_id=9, option_id=2))
    session.commit()
    return 9


def test_有纠错_拒绝删除(session: Session) -> None:
    dish_id = _seed(session, with_issue=True)
    with pytest.raises(BadRequest, match="下架"):
        delete_dish(session, dish_id=dish_id)


def test_零引用_允许删除(session: Session) -> None:
    dish_id = _seed(session, with_issue=False)
    delete_dish(session, dish_id=dish_id)
    assert session.get(Dish, dish_id) is None
