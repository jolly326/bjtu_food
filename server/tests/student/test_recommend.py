"""一句话搜索（docs/func/client/A-浏览与发现/A1-搜索找菜.md、docs/api/client/dishes.md）。

覆盖三类硬约束（完整菜名 / 食堂名 / 价格上限）、余弦排序、向量模型一致性回退与结果上限。
"""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session

from app.core.runtime.cache import invalidate
from app.core.errors import BadRequest
from app.models import Base, Canteen, Dish, DishOffering, Stall
from app.student.dish.service import recommend


@pytest.fixture
def session() -> Iterator[Session]:
    """内存库；每个用例前清空进程内缓存，避免上一个用例的在售行被复用。"""
    engine = create_engine("sqlite://")

    @event.listens_for(engine, "connect")
    def _enable_fk(dbapi_conn, _record):
        dbapi_conn.execute("PRAGMA foreign_keys=ON")

    Base.metadata.create_all(engine)
    invalidate("student:dishes")
    with Session(engine) as s:
        yield s
    invalidate("student:dishes")


def _seed(session: Session) -> None:
    session.add_all(
        [
            Canteen(id=1, name="学一食堂"),
            Canteen(id=2, name="学二食堂"),
            Stall(id=1, canteen_id=1, name="川味小炒"),
            Stall(id=2, canteen_id=2, name="家常菜"),
        ]
    )
    session.flush()
    session.add_all(
        [
            Dish(id=1, name="辣子鸡", description="川味 重辣 下饭"),
            Dish(id=2, name="宫保鸡丁", description="微辣 花生 下饭"),
            Dish(id=3, name="番茄炒蛋", description="清淡 酸甜 家常"),
            # 无在售售卖点的菜品不进入搜索
            Dish(id=4, name="水煮鱼", description="麻辣 川味"),
        ]
    )
    session.flush()
    session.add_all(
        [
            DishOffering(id=1, dish_id=1, canteen_id=1, stall_id=1, price=1200, activate=True),
            DishOffering(id=2, dish_id=2, canteen_id=1, stall_id=1, price=1500, activate=True),
            DishOffering(id=3, dish_id=2, canteen_id=2, stall_id=2, price=1800, activate=True),
            DishOffering(id=4, dish_id=3, canteen_id=2, stall_id=2, price=800, activate=True),
        ]
    )
    session.commit()


def test_空查询_拒绝(session: Session) -> None:
    with pytest.raises(BadRequest):
        recommend(session, query="   ")


def test_超长查询_拒绝(session: Session) -> None:
    with pytest.raises(BadRequest):
        recommend(session, query="辣" * 201)


def test_停售菜品不进入结果(session: Session) -> None:
    _seed(session)
    names = {dish.name for dish in recommend(session, query="川味")}
    assert "水煮鱼" not in names


def test_完整菜名命中_只返回该菜(session: Session) -> None:
    _seed(session)
    dishes = recommend(session, query="辣子鸡")
    assert [dish.name for dish in dishes] == ["辣子鸡"]


def test_食堂名命中_只保留该食堂售卖点(session: Session) -> None:
    _seed(session)
    dishes = recommend(session, query="学一食堂有什么下饭的")
    assert {dish.name for dish in dishes} == {"辣子鸡", "宫保鸡丁"}
    for dish in dishes:
        assert [loc.canteen_name for loc in dish.locations] == ["学一食堂"]


def test_价格上限_只保留符合价格的售卖点(session: Session) -> None:
    _seed(session)
    dishes = recommend(session, query="10 元以内清淡的")
    assert {dish.name for dish in dishes} == {"番茄炒蛋"}
    assert dishes[0].locations[0].price == 800


def test_向量模型不符_实时重算仍可返回(session: Session) -> None:
    _seed(session)
    dish = session.get(Dish, 1)
    assert dish is not None
    dish.embedding = [0.0] * 1024  # 伪向量：与任何 query 相似度均为 0
    dish.embedding_model = "other-model"
    session.commit()

    dishes = recommend(session, query="重辣下饭")
    assert dishes, "模型不符时应按当前模型实时计算，而不是直接丢弃"
    assert dishes[0].name == "辣子鸡"


def test_结果上限_最多30条(session: Session) -> None:
    _seed(session)
    session.add_all(
        [
            Dish(id=100 + i, name=f"测试菜{i:02d}", description="川味 下饭")
            for i in range(35)
        ]
    )
    session.flush()
    session.add_all(
        [
            DishOffering(
                id=1000 + i,
                dish_id=100 + i,
                canteen_id=1,
                stall_id=1,
                price=1000,
                activate=True,
            )
            for i in range(35)
        ]
    )
    session.commit()

    assert len(recommend(session, query="川味下饭")) <= 30


def test_相似度降序(session: Session) -> None:
    _seed(session)
    dishes = recommend(session, query="清淡 家常")
    assert dishes[0].name == "番茄炒蛋"
