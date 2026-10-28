"""auth 域业务规则（docs/func/client/C1-openid静默识别.md）。"""

from __future__ import annotations

import logging

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import BadRequest
from app.core.security import STUDENT, create_token
from app.models import User

logger = logging.getLogger(__name__)

_WECHAT_CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session"
_CLIENT_TIMEOUT = 5.0

_client: httpx.Client | None = None


def _wechat_client() -> httpx.Client:
    """进程级共享客户端：复用 TCP / TLS 连接，免去每次登录重新握手。"""
    global _client
    if _client is None:
        _client = httpx.Client(timeout=_CLIENT_TIMEOUT)
    return _client


def close_http_client() -> None:
    """释放连接池（应用关闭时由 lifespan 调用）。"""
    global _client
    if _client is not None:
        _client.close()
        _client = None


def exchange_code_for_openid(code: str) -> str:
    """用 wx.login 的 code 换取 openid。

    调微信接口失败 / 返回错误 ⇒ 视为 code 无效（400），不建用户。
    """
    if not settings.wechat_appid or not settings.wechat_secret:
        raise BadRequest("服务端未配置微信小程序凭证")

    try:
        resp = _wechat_client().get(
            _WECHAT_CODE2SESSION,
            params={
                "appid": settings.wechat_appid,
                "secret": settings.wechat_secret,
                "js_code": code,
                "grant_type": "authorization_code",
            },
        )
        data = resp.json()
    except (httpx.HTTPError, ValueError) as exc:
        logger.warning("jscode2session 请求失败：%s", exc)
        raise BadRequest("微信登录失败，请重试") from exc

    openid = data.get("openid")
    if not openid:
        errcode = data.get("errcode")
        errmsg = data.get("errmsg", "")
        # 保留微信原始错误码：用于区分 appid/secret 不匹配、code 失效、IP 白名单等
        logger.warning("jscode2session 失败：errcode=%s errmsg=%s", errcode, errmsg)
        raise BadRequest(f"code 无效或已过期（微信 errcode={errcode}）")
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
