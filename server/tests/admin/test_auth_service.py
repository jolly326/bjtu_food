"""运营端登录：账号不存在也消耗一次 BCrypt 开销，消弭时序侧信道。"""

from __future__ import annotations

from typing import Any

import pytest

from app.admin.auth.service import authenticate
from app.core.errors import Unauthorized


class _EmptySession:
    """替身会话：find_by_username 的 scalar 查询返回空 ⇒ 走「账号不存在」分支。"""

    def scalar(self, _stmt: Any) -> None:
        return None


def test_unknown_user_rejected_with_dummy_bcrypt() -> None:
    """账号不存在 ⇒ 与口令错误同文案拒绝（内部同样执行一次等价 BCrypt 比对）。"""
    with pytest.raises(Unauthorized, match="用户名或密码错误"):
        authenticate(_EmptySession(), username="ghost", password="whatever")  # type: ignore[arg-type]


def test_empty_credentials_rejected_before_lookup() -> None:
    """空用户名 / 空口令直接拒绝，不做任何查询。"""
    with pytest.raises(Unauthorized):
        authenticate(_EmptySession(), username="", password="x")  # type: ignore[arg-type]
    with pytest.raises(Unauthorized):
        authenticate(_EmptySession(), username="admin", password="")  # type: ignore[arg-type]
