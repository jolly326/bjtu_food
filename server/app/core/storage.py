"""图片路径工具：库内**相对路径** <-> 出参**绝对 URL**。

口径（docs/schema/poster.md、dish.md）：库内一律存相对路径；出参转绝对 URL。
"""

from __future__ import annotations

from app.core.config import settings

_HTTP_PREFIXES = ("http://", "https://")


def to_absolute(relative: str | None) -> str | None:
    """相对路径 -> 绝对 URL；未配置域名时原样返回（仅开发期）。"""
    if not relative:
        return None
    if relative.startswith(_HTTP_PREFIXES):
        return relative
    domain = settings.cos_domain.strip().rstrip("/")
    if not domain:
        return relative
    return f"{domain}/{relative.lstrip('/')}"


def to_relative(url: str | None) -> str | None:
    """绝对 URL -> 入库相对路径（运营端上传后入库用）。"""
    if not url:
        return None
    domain = settings.cos_domain.strip().rstrip("/")
    if domain and url.startswith(f"{domain}/"):
        return url[len(domain) + 1 :]
    return url
