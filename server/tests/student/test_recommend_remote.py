"""外部向量服务下的召回策略：跳过未回填行 + 精排失败降级（docs/api/client/dishes.md）。"""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.core.search import rerank as rerank_mod
from app.core.runtime.cache import invalidate
from app.core.config import settings
from app.models import Base, Canteen, Dish, DishOffering, Stall
from app.student.dish import service

REMOTE_MODEL = "test/bge-m3"


@pytest.fixture
def session() -> Iterator[Session]:
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    invalidate("student:dishes")
    with Session(engine) as s:
        yield s
    invalidate("student:dishes")


@pytest.fixture
def remote(monkeypatch: pytest.MonkeyPatch) -> str:
    """切到外部向量服务（不真打网络）：query 向量与菜品向量都在同一二维空间。"""
    monkeypatch.setattr(settings, "embedding_provider", "http")
    monkeypatch.setattr(settings, "embedding_model", REMOTE_MODEL)
    monkeypatch.setattr(settings, "system1_enabled", False)
    monkeypatch.setattr(settings, "system1_role", "rerank")
    monkeypatch.setattr(settings, "system1_top_k", 10)
    monkeypatch.setattr(service, "current_model", lambda: REMOTE_MODEL)
    monkeypatch.setattr(service, "embed", lambda text: [1.0, 0.0])
    return REMOTE_MODEL


def _seed(session: Session, *, model: str, with_vectors: bool = True) -> None:
    session.add_all(
        [Canteen(id=1, name="学一食堂"), Stall(id=1, canteen_id=1, name="川味小炒")]
    )
    session.flush()
    dishes = [
        Dish(id=1, name="辣子鸡", description="川味 重辣"),
        Dish(id=2, name="番茄炒蛋", description="清淡 家常"),
    ]
    if with_vectors:
        dishes[0].embedding = [1.0, 0.0]
        dishes[0].embedding_model = model
        dishes[1].embedding = [0.0, 1.0]
        dishes[1].embedding_model = model
    # 未回填（或模型不符）的菜品
    dishes.append(Dish(id=3, name="待回填菜", description="川味"))
    session.add_all(dishes)
    session.flush()
    session.add_all(
        [
            DishOffering(id=1, dish_id=1, canteen_id=1, stall_id=1, price=1200, activate=True),
            DishOffering(id=2, dish_id=2, canteen_id=1, stall_id=1, price=800, activate=True),
            DishOffering(id=3, dish_id=3, canteen_id=1, stall_id=1, price=1000, activate=True),
        ]
    )
    session.commit()


def test_未回填的行被跳过(session: Session, remote: str) -> None:
    _seed(session, model=remote)
    names = [dish.name for dish in service.recommend(session, query="川味")]
    assert names == ["辣子鸡", "番茄炒蛋"], "模型不符 / 缺向量的行不应发起实时补算"


def test_模型不符的行被跳过(session: Session, remote: str) -> None:
    _seed(session, model="old-model")
    assert service.recommend(session, query="川味") == []


def test_精排失败_退回向量排序(
    session: Session, remote: str, monkeypatch: pytest.MonkeyPatch
) -> None:
    _seed(session, model=remote)
    monkeypatch.setattr(settings, "system1_enabled", True)
    monkeypatch.setattr(rerank_mod, "rerank", lambda query, docs, top_k: None)

    names = [dish.name for dish in service.recommend(session, query="川味")]
    assert names == ["辣子鸡", "番茄炒蛋"]


def test_精排成功_按精排顺序返回(
    session: Session, remote: str, monkeypatch: pytest.MonkeyPatch
) -> None:
    _seed(session, model=remote)
    monkeypatch.setattr(settings, "system1_enabled", True)
    seen: dict[str, list[str]] = {}

    def fake_rerank(query: str, documents: list[str], top_k: int) -> list[int]:
        seen["documents"] = documents
        return [1, 0]

    monkeypatch.setattr(rerank_mod, "rerank", fake_rerank)

    names = [dish.name for dish in service.recommend(session, query="川味")]
    assert names == ["番茄炒蛋", "辣子鸡"]
    assert seen["documents"] == ["川味 重辣", "清淡 家常"]


def test_相同查询命中缓存_不重复调用精排(
    session: Session, remote: str, monkeypatch: pytest.MonkeyPatch
) -> None:
    _seed(session, model=remote)
    monkeypatch.setattr(settings, "system1_enabled", True)
    calls: list[int] = []

    def fake_rerank(query: str, documents: list[str], top_k: int) -> list[int]:
        calls.append(1)
        return [0, 1]

    monkeypatch.setattr(rerank_mod, "rerank", fake_rerank)

    first = [d.name for d in service.recommend(session, query="川味")]
    second = [d.name for d in service.recommend(session, query="川味")]
    assert first == second
    assert calls == [1], "同一句话 30 秒内应复用结果，不重复付费调用"


def test_精排关闭_不发起调用(
    session: Session, remote: str, monkeypatch: pytest.MonkeyPatch
) -> None:
    _seed(session, model=remote)
    calls: list[int] = []

    def fake_rerank(query: str, documents: list[str], top_k: int) -> list[int]:
        calls.append(1)
        return [1, 0]

    monkeypatch.setattr(rerank_mod, "rerank", fake_rerank)
    service.recommend(session, query="川味")
    assert calls == []
