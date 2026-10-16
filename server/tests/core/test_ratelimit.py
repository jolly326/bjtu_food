"""进程内固定窗口限频测试（docs/secur/S-03）。"""

from __future__ import annotations

import pytest

from app.core.errors import RateLimited
from app.core.ratelimit import FixedWindowCounter, Rule, check


def test_allows_exactly_limit_then_blocks() -> None:
    counter = FixedWindowCounter()
    rule = Rule(3, 60)
    for _ in range(3):
        allowed, _ = counter.hit("k", rule)
        assert allowed is True
    allowed, retry_after = counter.hit("k", rule)
    assert allowed is False
    assert retry_after >= 1


def test_window_resets_after_expiry() -> None:
    counter = FixedWindowCounter()
    rule = Rule(1, 0)  # 窗口 0 秒：下一次调用即视为新窗口
    allowed, _ = counter.hit("k", rule)
    assert allowed is True
    allowed, _ = counter.hit("k", rule)
    assert allowed is True


def test_check_raises_rate_limited_with_retry_seconds() -> None:
    rule = Rule(1, 60)
    check("unit-test-check-key", rule)
    with pytest.raises(RateLimited) as excinfo:
        check("unit-test-check-key", rule)
    assert "秒" in excinfo.value.message


def test_distinct_keys_do_not_interfere() -> None:
    counter = FixedWindowCounter()
    rule = Rule(1, 60)
    assert counter.hit("a", rule)[0] is True
    assert counter.hit("b", rule)[0] is True
    assert counter.hit("a", rule)[0] is False
