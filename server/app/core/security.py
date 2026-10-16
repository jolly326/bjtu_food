"""凭证：JWT（学生端 / 运营端双 secret）与 BCrypt。

口径（docs/secur/S-01、docs/api/README.md「鉴权」）：

* 学生端 JWT 有效期 7 天，端上缓存；``401`` ⇒ 端上静默重登并重试原请求一次
* 运营端 JWT 用**独立 secret** —— 与学生端不通用，学生 token 无法冒充运营端
* 🔴 端上**不持有、不传递 openid**；身份只由服务端从凭证解析
* 除 ``POST /auth/wechat-login`` 与 ``POST /admin/sessions`` 外，全部端点要求凭证
"""

from __future__ import annotations

import time
from typing import Any

import bcrypt
import jwt

from app.core.config import settings
from app.core.errors import Unauthorized

STUDENT = "student"
ADMIN = "admin"

_ALG = "HS256"


def _secret(kind: str) -> str:
    secret = settings.jwt_secret if kind == STUDENT else settings.admin_jwt_secret
    if not secret:
        raise Unauthorized("服务端未配置签名密钥")
    return secret


def create_token(subject: int, kind: str) -> str:
    now = int(time.time())
    payload = {
        "sub": str(subject),
        "kind": kind,
        "iat": now,
        "exp": now + settings.jwt_ttl_seconds,
    }
    return jwt.encode(payload, _secret(kind), algorithm=_ALG)


def decode_token(token: str, kind: str) -> dict[str, Any]:
    try:
        return jwt.decode(token, _secret(kind), algorithms=[_ALG])
    except jwt.PyJWTError as exc:
        raise Unauthorized("凭证无效或已过期") from exc


def current_subject(token: str, kind: str) -> int:
    """解出凭证主体 id；kind 不匹配视为无效凭证。"""
    payload = decode_token(token, kind)
    if payload.get("kind") != kind:
        raise Unauthorized("凭证类型不匹配")
    try:
        return int(payload["sub"])
    except (KeyError, TypeError, ValueError) as exc:
        raise Unauthorized("凭证无效") from exc


def extract_bearer(authorization: str | None) -> str:
    """从 Authorization 头取出 bearer token。"""
    if not authorization:
        raise Unauthorized("缺少凭证")
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise Unauthorized("凭证格式不正确")
    return token.strip()


# ---------- 口令哈希（运营端） ----------


def hash_password(plain: str) -> str:
    return bcrypt.hashpw(plain.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(plain.encode("utf-8"), hashed.encode("utf-8"))
    except ValueError:
        # 库里存的不是合法 BCrypt 哈希（例如误写明文）
        return False
