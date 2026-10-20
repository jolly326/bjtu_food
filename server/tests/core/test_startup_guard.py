"""启动预检：生产缺签名密钥 / 双端同密钥 ⇒ 拒绝启动（docs/secur/S-04 §一）。"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

import pytest

from app.core import config
from app.main import create_app

if TYPE_CHECKING:
    from pytest import LogCaptureFixture, MonkeyPatch


def test_missing_secrets_rejected_in_prod(monkeypatch: MonkeyPatch) -> None:
    monkeypatch.setattr(config.settings, "app_env", "prod")
    monkeypatch.setattr(config.settings, "jwt_secret", "")
    monkeypatch.setattr(config.settings, "admin_jwt_secret", "another-secret")
    with pytest.raises(RuntimeError, match="JWT_SECRET"):
        create_app()


def test_identical_secrets_rejected_in_prod(monkeypatch: MonkeyPatch) -> None:
    """双端同密钥 ⇒ 学生 token 可冒充运营端（S-01 红线），生产拒绝启动。"""
    monkeypatch.setattr(config.settings, "app_env", "prod")
    monkeypatch.setattr(config.settings, "jwt_secret", "same-secret-value")
    monkeypatch.setattr(config.settings, "admin_jwt_secret", "same-secret-value")
    with pytest.raises(RuntimeError, match="不得相同"):
        create_app()


def test_dev_mode_missing_secrets_only_warns(monkeypatch: MonkeyPatch) -> None:
    """dev 模式不拒绝启动（本地可先跑通路由），仅告警。"""
    monkeypatch.setattr(config.settings, "app_env", "dev")
    monkeypatch.setattr(config.settings, "jwt_secret", "")
    monkeypatch.setattr(config.settings, "admin_jwt_secret", "")
    create_app()  # 不抛异常即通过


def test_cors_star_warns_in_prod(monkeypatch: MonkeyPatch, caplog: LogCaptureFixture) -> None:
    monkeypatch.setattr(config.settings, "app_env", "prod")
    monkeypatch.setattr(config.settings, "cors_allow_origins", "*")
    with caplog.at_level(logging.WARNING, logger="app.main"):
        create_app()
    assert any("CORS" in msg for msg in caplog.messages)


def test_valid_config_starts_quietly(monkeypatch: MonkeyPatch, caplog: LogCaptureFixture) -> None:
    """密钥齐备且互不相同时：无预检告警。"""
    monkeypatch.setattr(config.settings, "app_env", "prod")
    monkeypatch.setattr(config.settings, "jwt_secret", "student-secret-0123456789abcdef")
    monkeypatch.setattr(config.settings, "admin_jwt_secret", "admin-secret-9876543210fedcba")
    with caplog.at_level(logging.WARNING, logger="app.main"):
        create_app()
    assert not [msg for msg in caplog.messages if "JWT" in msg or "CORS" in msg]
