"""运营端登录。

端点（docs/api/web/auth.md）：
    POST /admin/sessions   🔓 公开（换取运营端 JWT）

🔴 运营端 secret 与学生端**分离**；登录端点保留 IP 限频兜底。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from app.admin.auth import service
from app.core.db import get_session
from app.core.envelope import Envelope, ok
from app.core.ratelimit import RULE_ADMIN_LOGIN, check, client_ip
from app.core.schemas import InModel, OutModel

router = APIRouter(prefix="/admin", tags=["admin-auth"])


class AdminLoginIn(InModel):
    username: str
    password: str


class AdminSessionOut(OutModel):
    token: str
    username: str


@router.post("/sessions", response_model=Envelope[AdminSessionOut], summary="运营端登录")
def login(
    payload: AdminLoginIn,
    request: Request,
    session: Session = Depends(get_session),
) -> Envelope[AdminSessionOut]:
    check(f"admin-login:ip:{client_ip(request)}", RULE_ADMIN_LOGIN)
    admin = service.authenticate(session, username=payload.username.strip(), password=payload.password)
    token = service.issue_admin_token(admin.id)
    return ok(AdminSessionOut(token=token, username=admin.username))
