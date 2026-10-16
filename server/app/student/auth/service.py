"""auth 域业务规则（docs/func/client/C1-openid 静默识别.md）。"""

from __future__ import annotations

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import BadRequest
from app.core.security import STUDENT, create_token
from app.models import User

_WECHAT_CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session"


def exchange_code_for_openid(code: str) -> str:
    """用 wx.login 的 code 换取 openid。

    调微信接口失败 / 返回错误 ⇒ 视为 code 无效（400），不建用户。
    """
    if not settings.wechat_appid or not settings.wechat_secret:
        raise BadRequest("服务端未配置微信小程序凭证")

    try:
        resp = httpx.get(
            _WECHAT_CODE2SESSION,
            params={
                "appid": settings.wechat_appid,
                "secret": settings.wechat_secret,
                "js_code": code,
                "grant_type": "authorization_code",
            },
            timeout=5.0,
        )
        data = resp.json()
    except (httpx.HTTPError, ValueError) as exc:
        raise BadRequest("微信登录失败，请重试") from exc

    openid = data.get("openid")
    if not openid:
        raise BadRequest("code 无效或已过期")
    return str(openid)


def get_or_create_user(session: Session, openid: str) -> int:
    """按 openid 建 / 取用户行（uk_user_openid 唯一）。"""
    existing = session.scalar(select(User.id).where(User.openid == openid))
    if existing is not None:
        return int(existing)

    user = User(openid=openid)
    session.add(user)
    session.flush()  # 取到自增 id，事务由上层提交
    return int(user.id)


def issue_student_token(user_id: int) -> str:
    return create_token(user_id, STUDENT)
