"""FastAPI 依赖：解析当前凭证主体。

🔴 身份只由服务端从凭证解析（docs/secur/S-02）—— 请求体 / query 一律不接受用户标识。
除 ``POST /auth/wechat-login`` 与 ``POST /admin/sessions`` 外，端点都要求凭证。
"""

from __future__ import annotations

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.errors import Unauthorized
from app.core.security import ADMIN, STUDENT, current_subject

_bearer = HTTPBearer(auto_error=False, description="Bearer <token>")


def current_student_id(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> int:
    """当前学生 id（从学生端 token 解析）。"""
    if credentials is None:
        raise Unauthorized("缺少凭证")
    return current_subject(credentials.credentials, STUDENT)


def current_admin_id(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> int:
    """当前管理员 id（从运营端 token 解析）。"""
    if credentials is None:
        raise Unauthorized("缺少凭证")
    return current_subject(credentials.credentials, ADMIN)
