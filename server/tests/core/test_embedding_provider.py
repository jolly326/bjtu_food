"""向量服务 provider 切换（docs/secur/S-04、docs/schema/dish.md）。"""

from __future__ import annotations

import pytest

from app.core.search import embedding
from app.core.config import settings
from app.core.errors import InternalError


def test_本地模型_标识与归一化(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "embedding_provider", "local")
    assert embedding.current_model() == embedding.LOCAL_MODEL

    vec = embedding.embed("辣子鸡 重辣 下饭")
    assert len(vec) == embedding.DIM
    # 向量分量保留 6 位小数，模长误差在 1e-4 内即视为已归一化
    assert abs(sum(x * x for x in vec) - 1.0) < 1e-4


def test_空文本_返回零向量(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "embedding_provider", "local")
    assert embedding.embed("   ") == [0.0] * embedding.DIM


def test_外部模型_走http并归一化(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "embedding_provider", "http")
    monkeypatch.setattr(settings, "embedding_model", "test/bge-m3")
    monkeypatch.setattr(settings, "embedding_dim", 3)
    monkeypatch.setattr(embedding, "_http_embed", lambda text: [3.0, 0.0, 4.0])

    assert embedding.current_model() == "test/bge-m3"
    assert embedding.embed("任意文本") == [0.6, 0.0, 0.8]


def test_维度与配置不符_报500(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "embedding_provider", "http")
    monkeypatch.setattr(settings, "embedding_model", "test/bge-m3")
    monkeypatch.setattr(settings, "embedding_dim", 1024)
    monkeypatch.setattr(embedding, "_http_embed", lambda text: [1.0, 2.0])

    with pytest.raises(InternalError):
        embedding.embed("任意文本")


class _BoomClient:
    """模拟连接失败：POST 直接抛 httpx 异常。"""

    def __init__(self, *args: object, **kwargs: object) -> None:
        pass

    def __enter__(self) -> _BoomClient:
        return self

    def __exit__(self, *exc: object) -> bool:
        return False

    def post(self, *args: object, **kwargs: object) -> object:
        raise embedding.httpx.ConnectError("boom")


def test_外部服务异常_报500(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "embedding_provider", "http")
    monkeypatch.setattr(settings, "embedding_model", "test/bge-m3")
    monkeypatch.setattr(embedding.httpx, "Client", _BoomClient)

    with pytest.raises(InternalError):
        embedding.embed("任意文本")
