"""学生端端点级测试：鉴权 / 参数校验 / 限频分支。

这些用例**不连数据库**（依赖层拦截或参数校验先于数据访问），
可在无库环境下运行；数据访问的正确性由后续集成测试覆盖。
"""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.core import config
from app.core.errors import CODE_BAD_REQUEST, CODE_RATE_LIMITED, CODE_UNAUTHORIZED
from app.core.ratelimit import RULE_LIKE, limiter
from app.core.security import STUDENT, create_token
from app.main import app

STUDENT_SECRET = "student-secret-0123456789abcdef-0123456789abcdef"
ADMIN_SECRET = "admin-secret-9876543210fedcba-9876543210fedcba"


@pytest.fixture(autouse=True)
def _secrets(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(config.settings, "jwt_secret", STUDENT_SECRET)
    monkeypatch.setattr(config.settings, "admin_jwt_secret", ADMIN_SECRET)


@pytest.fixture(autouse=True)
def _isolated_limiter() -> Iterator[None]:
    """清空限频计数：全局 IP 兜底是进程内计数，用例之间需互不干扰。"""
    limiter.reset()
    yield
    limiter.reset()


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def _auth(user_id: int = 1) -> dict[str, str]:
    return {"Authorization": f"Bearer {create_token(user_id, STUDENT)}"}


def test_missing_token_returns_401(client: TestClient) -> None:
    """除 wechat-login 外，所有端点都要求凭证。"""
    resp = client.get("/canteens")
    assert resp.status_code == 200  # 信封模式：HTTP 恒 200
    assert resp.json()["code"] == CODE_UNAUTHORIZED


def test_malformed_token_returns_401(client: TestClient) -> None:
    resp = client.get("/canteens", headers={"Authorization": "Bearer not-a-jwt"})
    assert resp.json()["code"] == CODE_UNAUTHORIZED


def test_missing_option_id_returns_400(client: TestClient) -> None:
    resp = client.post("/dishes/1/issues", json={}, headers=_auth())
    assert resp.json()["code"] == CODE_BAD_REQUEST


def test_extra_identity_field_returns_400(client: TestClient) -> None:
    """请求体带 userId 一类身份字段 ⇒ 参数非法（docs/secur/S-02）。"""
    resp = client.post("/dishes/1/issues", json={"optionId": 1, "userId": 2}, headers=_auth())
    assert resp.json()["code"] == CODE_BAD_REQUEST


def test_like_rate_limit_returns_429(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """超过阈值的第 N+1 次请求返回 429 + 可等待秒数。"""
    from app.student.dish import service as dish_service

    monkeypatch.setattr(
        dish_service,
        "toggle_like",
        lambda *_args, **_kwargs: dish_service.DishLikeOut(id=1, like_count=1, liked=True),
    )

    headers = _auth(user_id=424242)
    last = None
    for _ in range(RULE_LIKE.limit + 1):
        last = client.post("/dishes/1/likes", headers=headers)

    assert last is not None
    assert last.json()["code"] == CODE_RATE_LIMITED
    assert "秒" in last.json()["message"]
