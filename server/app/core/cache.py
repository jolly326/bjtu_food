"""进程内 TTL 缓存（只读热点数据；单 worker 适用）。

用于学生端「全量在售菜品」这类读多写少、可容忍短时延迟的查询，
避免每次进地图都做一次多表 join。写端点不缓存；TTL 到期自动失效。
"""

from __future__ import annotations

import time
from collections.abc import Callable

_store: dict[str, tuple[float, object]] = {}


def ttl_cached[T](key: str, ttl: float, factory: Callable[[], T]) -> T:
    """命中未过期缓存直接返回；否则调用 ``factory`` 并缓存结果。"""
    now = time.monotonic()
    hit = _store.get(key)
    if hit is not None and hit[0] > now:
        return hit[1]  # type: ignore[return-value]
    value = factory()
    _store[key] = (now + ttl, value)
    return value


def invalidate(prefix: str = "") -> None:
    """清空缓存（``prefix`` 为空则全清，否则按前缀清）。"""
    if not prefix:
        _store.clear()
        return
    for key in [key for key in _store if key.startswith(prefix)]:
        _store.pop(key, None)
