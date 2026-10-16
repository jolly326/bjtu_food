"""学生端身份：微信静默登录换 openid，签发学生端 token。

端点（docs/api/client/auth.md）：
    POST /auth/wechat-login   🔓 公开（唯一免鉴权端点）
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.envelope import Envelope, ok
from app.core.ratelimit import RULE_WECHAT_LOGIN, check, client_ip
from app.core.schemas import InModel, OutModel
from app.student.auth import service

router = APIRouter(prefix="/auth", tags=["auth"])


class WechatLoginIn(InModel):
    code: str


class TokenOut(OutModel):
    token: str


@router.post("/wechat-login", response_model=Envelope[TokenOut], summary="微信静默登录")
def wechat_login(
    payload: WechatLoginIn,
    request: Request,
    session: Session = Depends(get_session),
) -> Envelope[TokenOut]:
    if not payload.code.strip():
        from app.core.errors import BadRequest

        raise BadRequest("code 不能为空")

    # IP 维度限频兜底（docs/secur/S-03）
    check(f"wechat-login:ip:{client_ip(request)}", RULE_WECHAT_LOGIN)

    openid = service.exchange_code_for_openid(payload.code.strip())
    user_id = service.get_or_create_user(session, openid)
    token = service.issue_student_token(user_id)
    return ok(TokenOut(token=token))
