"""凭证测试：双 secret 隔离、bearer 解析、BCrypt（docs/secur/S-01）。"""

from __future__ import annotations

from typing import TYPE_CHECKING

import pytest

from app.core import config
from app.core.errors import Unauthorized
from app.core.security import (
    ADMIN,
    STUDENT,
    create_token,
    current_subject,
    extract_bearer,
    hash_password,
    verify_password,
)

if TYPE_CHECKING:
    from pytest import MonkeyPatch

STUDENT_SECRET = "student-secret-0123456789abcdef-0123456789abcdef"
ADMIN_SECRET = "admin-secret-9876543210fedcba-9876543210fedcba"


@pytest.fixture(autouse=True)
def _isolated_secrets(monkeypatch: MonkeyPatch) -> None:
    """测试不依赖 .env：固定两套不同的 secret。"""
    monkeypatch.setattr(config.settings, "jwt_secret", STUDENT_SECRET)
    monkeypatch.setattr(config.settings, "admin_jwt_secret", ADMIN_SECRET)


def test_student_token_roundtrip() -> None:
    token = create_token(42, STUDENT)
    assert current_subject(token, STUDENT) == 42


def test_admin_token_roundtrip() -> None:
    token = create_token(7, ADMIN)
    assert current_subject(token, ADMIN) == 7


def test_secrets_are_not_interchangeable() -> None:
    """运营端用独立 secret：学生 token 无法冒充运营端。"""
    student_token = create_token(42, STUDENT)
    with pytest.raises(Unauthorized):
        current_subject(student_token, ADMIN)

    admin_token = create_token(42, ADMIN)
    with pytest.raises(Unauthorized):
        current_subject(admin_token, STUDENT)


def test_tampered_token_is_rejected() -> None:
    token = create_token(42, STUDENT)
    with pytest.raises(Unauthorized):
        current_subject(token[:-2] + "xx", STUDENT)


@pytest.mark.parametrize(
    "header",
    [None, "", "abc", "Bearer", "Token abc", "Bearer   "],
)
def test_extract_bearer_rejects_bad_headers(header: str | None) -> None:
    with pytest.raises(Unauthorized):
        extract_bearer(header)


def test_extract_bearer_accepts_valid_header() -> None:
    assert extract_bearer("Bearer abc.def") == "abc.def"
    assert extract_bearer("bearer abc") == "abc"


def test_password_hash_is_not_plaintext_and_verifies() -> None:
    hashed = hash_password("s3cret-pass")
    assert hashed != "s3cret-pass"
    assert hashed.startswith("$2")
    assert verify_password("s3cret-pass", hashed) is True
    assert verify_password("wrong-pass", hashed) is False


def test_verify_password_rejects_non_bcrypt_value() -> None:
    """库里若被误写明文，比对必须安全失败（不抛异常、不通过）。"""
    assert verify_password("whatever", "plain-text-not-a-hash") is False
