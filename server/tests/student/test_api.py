"""学生端端点级测试：鉴权 / 参数校验 / 限频分支。

这些用例**不连数据库**（依赖层拦截或参数校验先于数据访问），
可在无库环境下运行；数据访问的正确性由后续集成测试覆盖。
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.core import config
from app.core.errors import CODE_BAD_REQUEST, CODE_RATE_LIMITED, CODE_UNAUTHORIZED
from app.core.ratelimit import RULE_REACT
from app.core.security import STUDENT, create_token
from app.main import app

STUDENT_SECRET = "student-secret-0123456789abcdef-0123456789abcdef"
ADMIN_SECRET = "admin-secret-9876543210fedcba-9876543210fedcba"


@pytest.fixture(autouse=True)
def _secrets(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(config.settings, "jwt_secret", STUDENT_SECRET)
    monkeypatch.setattr(config.settings, "admin_jwt_secret", ADMIN_SECRET)


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


def test_invalid_reaction_returns_400(client: TestClient) -> None:
    """三态取值 5/3/1；其它值 400（校验先于数据访问）。"""
    for value in (0, 2, 4, 9):
        resp = client.patch("/my/records/1", json={"reaction": value}, headers=_auth())
        assert resp.json()["code"] == CODE_BAD_REQUEST


def test_missing_option_id_returns_400(client: TestClient) -> None:
    resp = client.post("/dishes/1/issues", json={}, headers=_auth())
    assert resp.json()["code"] == CODE_BAD_REQUEST


def test_rate_limit_returns_429(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """超过阈值的第 N+1 次请求返回 429 + 可等待秒数。"""
    # 让业务层返回假记录，专注验证限频横切
    from types import SimpleNamespace

    from app.student.record import service as record_service

    fake_record = SimpleNamespace(
        id=1,
        dish_id=1,
        name="测试菜",
        image_url=None,
        wanted_at="2026-01-01 00:00:00",
        reaction=5,
        flavor_level="S",
    )
    monkeypatch.setattr(record_service, "react", lambda *_args, **_kwargs: fake_record)

    headers = _auth(user_id=424242)
    last = None
    for _ in range(RULE_REACT.limit + 1):
        last = client.patch("/my/records/1", json={"reaction": 5}, headers=headers)

    assert last is not None
    assert last.json()["code"] == CODE_RATE_LIMITED
    assert "秒" in last.json()["message"]
