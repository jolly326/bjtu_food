"""应用级横切闸门：请求体上限、安全响应头、原生 404 归一（docs/secur/S-04 §三）。"""

from __future__ import annotations

from collections.abc import Iterator
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient

from app.core.errors import CODE_BAD_REQUEST, CODE_NOT_FOUND, CODE_RATE_LIMITED, CODE_UNAUTHORIZED
from app.core.ratelimit import RULE_READ_IP, limiter
from app.main import MAX_REQUEST_BYTES, app

if TYPE_CHECKING:
    from pytest import MonkeyPatch

STUDENT_SECRET = "student-secret-0123456789abcdef-0123456789abcdef"
ADMIN_SECRET = "admin-secret-9876543210fedcba-9876543210fedcba"


@pytest.fixture(autouse=True)
def _secrets(monkeypatch: MonkeyPatch) -> None:
    """测试不依赖 .env：固定两套不同的 secret（预检红线）。"""
    from app.core import config

    monkeypatch.setattr(config.settings, "jwt_secret", STUDENT_SECRET)
    monkeypatch.setattr(config.settings, "admin_jwt_secret", ADMIN_SECRET)


@pytest.fixture(autouse=True)
def _isolated_limiter() -> Iterator[None]:
    limiter.reset()
    yield
    limiter.reset()


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def test_oversized_body_rejected_before_parsing(client: TestClient) -> None:
    """超 6MB 请求体在中间件即被拒（Content-Length 预检，不读体）。"""
    resp = client.post(
        "/auth/wechat-login",
        content=b"{" + b"x" * MAX_REQUEST_BYTES + b"}",
        headers={"Content-Type": "application/json"},
    )
    assert resp.status_code == 200  # 信封模式：HTTP 恒 200
    body = resp.json()
    assert body["code"] == CODE_BAD_REQUEST
    assert "请求体过大" in body["message"]


def test_security_headers_on_responses(client: TestClient) -> None:
    """全部响应带 nosniff + no-store（docs/secur/S-04 §三）。"""
    resp = client.get("/canteens")  # 无凭证 ⇒ 401 信封
    assert resp.headers["X-Content-Type-Options"] == "nosniff"
    assert resp.headers["Cache-Control"] == "no-store"
    assert resp.json()["code"] == CODE_UNAUTHORIZED


def test_security_headers_on_rate_limit_envelope(client: TestClient) -> None:
    """中间件直出的兜底信封同样带安全响应头。"""
    last = None
    for _ in range(RULE_READ_IP.limit + 1):
        last = client.get("/canteens")
    assert last is not None
    assert last.json()["code"] == CODE_RATE_LIMITED
    assert last.headers["X-Content-Type-Options"] == "nosniff"


def test_unknown_path_normalized_to_envelope(client: TestClient) -> None:
    """未知路径的原生 404 归一为信封（「HTTP 恒 200」契约）。"""
    resp = client.get("/definitely-not-a-route")
    assert resp.status_code == 200
    assert resp.json()["code"] == CODE_NOT_FOUND


def test_wrong_method_normalized_to_envelope(client: TestClient) -> None:
    resp = client.delete("/canteens")
    assert resp.status_code == 200
    assert resp.json()["code"] == CODE_BAD_REQUEST


def test_healthz_unaffected(client: TestClient) -> None:
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert resp.json() == {"status": "up"}
