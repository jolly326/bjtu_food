"""运营端登录业务规则（docs/func/web/C1-管理员登录.md、docs/secur/S-01）。"""

from __future__ import annotations

from sqlalchemy.orm import Session

from app.admin.auth import repo
from app.core.errors import Unauthorized
from app.core.security import ADMIN, create_token, dummy_hash, verify_password
from app.models import Admin


def authenticate(session: Session, *, username: str, password: str) -> Admin:
    """账密比对；失败一律「用户名或密码错误」（不区分账号是否存在）。"""
    if not username or not password:
        raise Unauthorized("用户名或密码错误")

    admin = repo.find_by_username(session, username=username)
    if admin is None:
        # 账号不存在也做一次等价 BCrypt 开销：防「账号存在性」时序侧信道
        verify_password(password, dummy_hash())
        raise Unauthorized("用户名或密码错误")
    if not verify_password(password, admin.password):
        raise Unauthorized("用户名或密码错误")
    return admin


def issue_admin_token(admin_id: int) -> str:
    return create_token(admin_id, ADMIN)
