"""进程内固定窗口限频测试（docs/secur/S-03）。"""

from __future__ import annotations

import pytest

from app.core.config import settings
from app.core.errors import RateLimited
from app.core.runtime.ratelimit import FixedWindowCounter, Rule, check, client_ip


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


# ---------- client_ip：X-Forwarded-For 只取受信跳 ----------


class _FakeRequest:
    def __init__(self, headers: dict[str, str], peer: str = "10.0.0.9") -> None:
        self.headers = headers
        self.client = type("_Client", (), {"host": peer})()


def test_client_ip_uses_peer_when_no_forwarded_header() -> None:
    assert client_ip(_FakeRequest({})) == "10.0.0.9"


def test_client_ip_ignores_spoofed_left_hops(monkeypatch: pytest.MonkeyPatch) -> None:
    """客户端自填的最左段不参与取址：从右数第 1 跳（反代追加的真实地址）生效。"""
    monkeypatch.setattr(settings, "forwarded_hops", 1)
    request = _FakeRequest({"x-forwarded-for": "1.1.1.1, 2.2.2.2, 3.3.3.3"})
    assert client_ip(request) == "3.3.3.3"


def test_client_ip_supports_multi_hop_chain(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "forwarded_hops", 2)
    request = _FakeRequest({"x-forwarded-for": "1.1.1.1, 2.2.2.2, 3.3.3.3"})
    assert client_ip(request) == "2.2.2.2"


def test_client_ip_can_ignore_forwarded_header_entirely(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "forwarded_hops", 0)
    request = _FakeRequest({"x-forwarded-for": "1.1.1.1"})
    assert client_ip(request) == "10.0.0.9"
