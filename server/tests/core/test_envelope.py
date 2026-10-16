"""信封与分页壳测试（docs/api/README.md「响应信封」「分页结构」）。"""

from __future__ import annotations

from app.core.envelope import (
    SUCCESS_CODE,
    SUCCESS_MESSAGE,
    AdminPageResult,
    PageResult,
    ok,
    page_ok,
)


def test_ok_defaults() -> None:
    env = ok()
    assert env.code == SUCCESS_CODE == 200
    assert env.message == SUCCESS_MESSAGE
    assert env.data is None


def test_ok_with_data() -> None:
    env = ok({"a": 1})
    assert env.data == {"a": 1}


def test_envelope_serializes_three_keys() -> None:
    payload = ok(None).model_dump()
    assert set(payload.keys()) == {"code", "message", "data"}


def test_student_page_shell_has_only_records() -> None:
    env = page_ok([1, 2, 3])
    assert isinstance(env.data, PageResult)
    assert env.data.records == [1, 2, 3]
    # 学生端不回传 total
    assert "total" not in env.data.model_dump()


def test_admin_page_shell_has_total() -> None:
    env = page_ok([1, 2], total=99)
    assert isinstance(env.data, AdminPageResult)
    assert env.data.records == [1, 2]
    assert env.data.total == 99
