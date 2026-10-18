"""图片路径工具：库内**相对路径** <-> 出参**绝对 URL**。

口径（docs/schema/poster.md、dish.md）：库内一律存相对路径；出参转绝对 URL。
"""

from __future__ import annotations

from app.core.config import require_cos_domain

_HTTP_PREFIXES = ("http://", "https://")


def to_absolute(relative: str | None) -> str | None:
    """相对路径 -> 绝对 URL；出参恒为绝对 URL（域名缺失在启动时即拒绝）。"""
    if not relative:
        return None
    if relative.startswith(_HTTP_PREFIXES):
        return relative
    return f"{require_cos_domain()}/{relative.lstrip('/')}"


def to_relative(url: str | None) -> str | None:
    """绝对 URL -> 入库相对路径（运营端上传后入库用）。"""
    if not url:
        return None
    domain = require_cos_domain()
    if url.startswith(f"{domain}/"):
        return url[len(domain) + 1 :]
    return url
