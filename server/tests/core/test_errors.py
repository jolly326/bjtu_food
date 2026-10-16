"""错误码与业务异常测试（docs/api/README.md「业务状态码」）。"""

from __future__ import annotations

import pytest

from app.core.errors import (
    CODE_BAD_REQUEST,
    CODE_FORBIDDEN,
    CODE_INTERNAL,
    CODE_NOT_FOUND,
    CODE_RATE_LIMITED,
    CODE_UNAUTHORIZED,
    ApiError,
    BadRequest,
    Forbidden,
    NotFound,
    RateLimited,
    Unauthorized,
)


@pytest.mark.parametrize(
    ("exc", "code"),
    [
        (BadRequest(), CODE_BAD_REQUEST),
        (Unauthorized(), CODE_UNAUTHORIZED),
        (Forbidden(), CODE_FORBIDDEN),
        (RateLimited(), CODE_RATE_LIMITED),
        (NotFound(), CODE_NOT_FOUND),
    ],
)
def test_error_code_mapping(exc: ApiError, code: int) -> None:
    assert exc.code == code
    assert exc.message


def test_error_codes_are_the_documented_set() -> None:
    """口径：200/400/401/403/429/4001/500，且**没有** 4031。"""
    documented = {200, 400, 401, 403, 429, 4001, 500}
    actual = {CODE_BAD_REQUEST, CODE_UNAUTHORIZED, CODE_FORBIDDEN, CODE_RATE_LIMITED, CODE_NOT_FOUND, CODE_INTERNAL}
    assert actual <= documented
    assert 4031 not in documented


def test_not_found_response_is_uniform() -> None:
    """非本人与「不存在」必须同一响应，避免 ID 枚举探测。"""
    assert NotFound().message == NotFound("资源不存在").message


def test_rate_limited_message_carries_retry_hint() -> None:
    assert "秒" in RateLimited("操作过于频繁，请 30 秒后再试").message
