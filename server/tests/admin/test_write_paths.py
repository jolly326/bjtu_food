"""运营端写路径的一致性约束（docs/func/web/A-主数据维护/*、docs/schema/dish_location.md）。

覆盖三类此前无测试的边界：
* 档口换食堂时 ``dish_location.canteen_id`` 随之迁移（反范式列与档口保持一致）
* 位置整表替换：同 (食堂, 档口) 先删后建、保留传入 id、外部 id 拒绝
* 反馈「标记已处理」幂等且不覆盖既有时间戳
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, field
from datetime import datetime

import pytest
from sqlalchemy import create_engine, event, select
from sqlalchemy.orm import Session

from app.admin.dish import service as dish_service
from app.admin.feedback import service as feedback_service
from app.admin.stall import service as stall_service
from app.core.errors import BadRequest
from app.models import Base, Canteen, Dish, DishLocation, Feedback, Stall, User


@pytest.fixture
def session() -> Iterator[Session]:
    engine = create_engine("sqlite://")

    @event.listens_for(engine, "connect")
    def _enable_fk(dbapi_conn, _record):
        dbapi_conn.execute("PRAGMA foreign_keys=ON")

    Base.metadata.create_all(engine)
    with Session(engine) as s:
        yield s


@dataclass
class _Location:
    canteen_id: int
    stall_id: int | None = None
    activate: bool = True
    id: int | None = None


@dataclass
class _DishPayload:
    name: str = "辣子鸡"
    description: str = "川味 重辣 下饭"
    image_url: str | None = None
    price: int | None = 1200
    locations: list[_Location] = field(default_factory=list)


@dataclass
class _StallPayload:
    canteen_id: int
    name: str = "川味小炒"
    floor: str = "二层"
    window_no: str | None = None
    activate: bool = True
    image_url: str | None = None


def _seed(session: Session) -> None:
    """按外键依赖分批落库：先主数据，再菜品，最后位置行。"""
    session.add_all(
        [
            User(id=1, openid="o1", created_at=datetime(2026, 1, 1)),
            Canteen(id=1, name="学一食堂"),
            Canteen(id=2, name="学二食堂"),
            Stall(id=1, canteen_id=1, name="川味小炒", floor="二层"),
        ]
    )
    session.flush()
    session.add(Dish(id=9, name="辣子鸡", description="川味 重辣 下饭", price=1200))
    session.flush()
    session.add(DishLocation(id=11, dish_id=9, canteen_id=1, stall_id=1, activate=True))
    session.flush()


def test_档口换食堂_位置行食堂同步迁移(session: Session) -> None:
    _seed(session)

    stall_service.update(session, stall_id=1, payload=_StallPayload(canteen_id=2))

    location = session.get(DishLocation, 11)
    assert location is not None
    assert location.canteen_id == 2


def test_位置整表替换_同键先删后建不冲突(session: Session) -> None:
    _seed(session)

    # 同一 (食堂 1, 档口 1) 不带 id 重新提交：等价于「删掉旧行再新建同键行」
    dish_service.update(
        session,
        dish_id=9,
        payload=_DishPayload(locations=[_Location(canteen_id=1, stall_id=1)]),
    )

    rows = list(session.scalars(select(DishLocation).where(DishLocation.dish_id == 9)))
    assert len(rows) == 1
    assert (rows[0].canteen_id, rows[0].stall_id) == (1, 1)


def test_位置整表替换_保留传入id(session: Session) -> None:
    _seed(session)

    dish_service.update(
        session,
        dish_id=9,
        payload=_DishPayload(
            locations=[
                _Location(id=11, canteen_id=1, stall_id=1, activate=False),
                _Location(canteen_id=2, stall_id=None),
            ]
        ),
    )

    rows = {
        loc.id: loc
        for loc in session.scalars(select(DishLocation).where(DishLocation.dish_id == 9))
    }
    assert set(rows) - {11} and 11 in rows
    assert rows[11].activate is False
    assert (rows[11].canteen_id, rows[11].stall_id) == (1, 1)


def test_位置id不属于该菜品_返回400(session: Session) -> None:
    _seed(session)

    with pytest.raises(BadRequest):
        dish_service.update(
            session,
            dish_id=9,
            payload=_DishPayload(locations=[_Location(id=999, canteen_id=1, stall_id=1)]),
        )


def test_标记已处理_幂等且不改写既有时间(session: Session) -> None:
    _seed(session)
    session.add(Feedback(id=5, type="dish", dish_id=9, user_id=1, content="偏咸"))
    session.commit()

    first = feedback_service.set_handled(session, feedback_id=5)
    session.commit()
    stamped = first.handled_at
    assert stamped is not None

    second = feedback_service.set_handled(session, feedback_id=5)
    session.commit()
    assert second.handled_at == stamped
