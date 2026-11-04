"""测试公共配置。

SQLite 只对 ``INTEGER PRIMARY KEY`` 自增，而本仓主键为 ``BIGINT``（对齐 MySQL 的
``BIGINT AUTO_INCREMENT``）。在 sqlite 方言下把 ``BIGINT`` 编译成 ``INTEGER``，
让测试库的自增 / 外键行为与生产库一致；MySQL 方言不受影响。
"""

from __future__ import annotations

import pytest
from sqlalchemy import BigInteger
from sqlalchemy.ext.compiler import compiles

from app.core.runtime.cache import invalidate
from app.core.config import settings


@compiles(BigInteger, "sqlite")
def _bigint_as_integer(_element: BigInteger, _compiler: object, **_kw: object) -> str:
    return "INTEGER"


@pytest.fixture(autouse=True)
def _clear_ttl_cache() -> None:
    """进程级 TTL 缓存跨用例隔离，避免串数据。"""
    invalidate()


@pytest.fixture(autouse=True)
def _offline_search(monkeypatch: pytest.MonkeyPatch) -> None:
    """搜索一律走本地向量、关闭精排：测试不得读真实 ``.env`` 里的外部服务配置去打真网。

    需要验证 ``http`` provider / 精排的用例，自行在用例内 ``monkeypatch`` 覆盖。
    """
    monkeypatch.setattr(settings, "embedding_provider", "local")
    monkeypatch.setattr(settings, "embedding_model", "")
    monkeypatch.setattr(settings, "embedding_api_key", "")
    monkeypatch.setattr(settings, "embedding_dim", 0)
    monkeypatch.setattr(settings, "system1_enabled", False)
