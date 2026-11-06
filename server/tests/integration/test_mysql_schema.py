"""MySQL 集成用例：验证「生产库才有的约束」——唯一索引、外键级联 / 限制、列长度严格模式。

SQLite 单测覆盖不到这些行为（内存库把 BIGINT 改成 INTEGER、默认不启用严格模式），
因此本模块**只在显式提供测试库时运行**：

    TEST_MYSQL_URL='mysql+pymysql://user:pass@host:3306/bjtu_food_test?charset=utf8mb4' \
        .venv/Scripts/python.exe -m pytest tests/integration -q

🔴 安全红线：库名必须含 ``test``，否则本模块直接跳过 —— 防止误连生产库建表 / 删表。
"""

from __future__ import annotations

import os
from collections.abc import Iterator
from urllib.parse import urlsplit

import pytest
from sqlalchemy import create_engine, event, select
from sqlalchemy.engine import Engine
from sqlalchemy.exc import DataError, IntegrityError
from sqlalchemy.orm import Session

from app.models import Base, Canteen, Dish, DishLocation, Stall

_URL = (os.environ.get("TEST_MYSQL_URL") or "").strip()


def _is_safe_target(url: str) -> bool:
    return "test" in urlsplit(url).path.lstrip("/").lower()


pytestmark = [
    pytest.mark.integration,
    pytest.mark.skipif(not _URL, reason="未提供 TEST_MYSQL_URL（MySQL 集成用例按需运行）"),
    pytest.mark.skipif(bool(_URL) and not _is_safe_target(_URL), reason="库名不含 test，拒绝执行"),
]


@pytest.fixture(scope="module")
def engine() -> Iterator[Engine]:
    engine = create_engine(_URL, future=True)

    @event.listens_for(engine, "connect")
    def _strict_mode(dbapi_conn, _record):
        # 对齐生产：严格模式下列超长 / 越界直接报错，而不是静默截断
        cursor = dbapi_conn.cursor()
        try:
            cursor.execute("SET SESSION sql_mode='STRICT_ALL_TABLES'")
        finally:
            cursor.close()

    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    try:
        yield engine
    finally:
        Base.metadata.drop_all(engine)
        engine.dispose()


@pytest.fixture
def session(engine: Engine) -> Iterator[Session]:
    with Session(engine) as s:
        s.add_all(
            [
                Canteen(id=1, name="学一食堂"),
                Canteen(id=2, name="学二食堂"),
                Stall(id=1, canteen_id=1, name="川味小炒", floor="二层"),
                Dish(id=9, name="辣子鸡", description="川味 重辣 下饭", price=1200),
                DishLocation(id=11, dish_id=9, canteen_id=1, stall_id=1, activate=True),
            ]
        )
        s.commit()
        yield s
        # 用例之间互不残留
        for table in reversed(Base.metadata.sorted_tables):
            s.execute(table.delete())
        s.commit()


def test_唯一索引拒绝同一菜品的同一位置(session: Session) -> None:
    session.add(DishLocation(id=12, dish_id=9, canteen_id=1, stall_id=1, activate=True))
    with pytest.raises(IntegrityError):
        session.flush()


def test_删菜级联清除位置(session: Session) -> None:
    session.delete(session.get(Dish, 9))
    session.commit()
    assert session.scalar(select(DishLocation.id).where(DishLocation.dish_id == 9)) is None


def test_食堂被位置引用时拒删(session: Session) -> None:
    session.delete(session.get(Canteen, 1))
    with pytest.raises(IntegrityError):
        session.commit()


def test_列长度严格模式下超长即报错(session: Session) -> None:
    session.add(Canteen(id=3, name="超" * 33))  # 列定义 String(32)
    with pytest.raises(DataError):
        session.flush()
