"""微信内容安全检测（``msgSecCheck``）——文本 UGC 的硬门槛。

生产必须配置 ``WECHAT_APPID`` / ``WECHAT_SECRET``（缺失时启动预检即拒绝）；
开发档未配置时放行并告警，便于本地联调。
"""

from __future__ import annotations

import logging

import httpx

from app.core.config import is_production, settings
from app.core.errors import BadRequest

logger = logging.getLogger(__name__)

_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token"
_CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check"
_TIMEOUT = 5.0

_client: httpx.Client | None = None


def _http() -> httpx.Client:
    global _client
    if _client is None:
        _client = httpx.Client(timeout=_TIMEOUT)
    return _client


def close_http_client() -> None:
    """释放连接池（应用关闭时由 lifespan 调用）。"""
    global _client
    if _client is not None:
        _client.close()
        _client = None


def _access_token() -> str:
    resp = _http().get(
        _TOKEN_URL,
        params={
            "grant_type": "client_credential",
            "appid": settings.wechat_appid,
            "secret": settings.wechat_secret,
        },
    )
    resp.raise_for_status()
    data = resp.json()
    token = data.get("access_token")
    if not token:
        logger.warning("获取 access_token 失败：errcode=%s", data.get("errcode"))
        raise BadRequest("内容安全检测不可用，请稍后重试")
    return str(token)


def check_text(content: str) -> None:
    """文本送检；不合规 / 调用失败均抛 ``BadRequest``。"""
    if not settings.wechat_appid or not settings.wechat_secret:
        if is_production():
            raise BadRequest("内容安全服务未配置")
        logger.warning("未配置微信凭证：跳过内容安全检测（仅开发环境）")
        return

    try:
        token = _access_token()
        resp = _http().post(
            _CHECK_URL,
            params={"access_token": token},
            json={"content": content, "version": 2, "scene": 2},
        )
        resp.raise_for_status()
        data = resp.json()
    except (httpx.HTTPError, ValueError) as exc:
        logger.warning("msgSecCheck 调用失败：%s", exc)
        raise BadRequest("内容安全检测失败，请稍后重试") from exc

    if data.get("errcode") not in (0, None):
        raise BadRequest(f"内容安全检测失败（errcode={data.get('errcode')}）")
    if (data.get("result") or {}).get("suggest") != "pass":
        raise BadRequest("内容不合规，请修改后重试")
