"""运营端图片上传业务规则（docs/api/web/images.md）。

* 类型白名单 jpg / png / webp（**按文件内容魔数判**，不信任 Content-Type）
* 单张 ≤ 5MB
* 转存腾讯云 COS，返回**绝对 URL**（运营端提交表单时再转相对路径入库）
"""

from __future__ import annotations

import uuid

from app.core.config import settings
from app.core.errors import BadRequest, InternalError
from app.core.storage import to_absolute

ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_BYTES = 5 * 1024 * 1024

# 魔数 -> 规范化 Content-Type（防止扩展名 / 头伪装）
_MAGIC = (
    (b"\xff\xd8\xff", "image/jpeg"),
    (b"\x89PNG\r\n\x1a\n", "image/png"),
    (b"RIFF", "image/webp"),  # WEBP: RIFF....WEBP
)


def _sniff(content: bytes) -> str | None:
    for magic, mime in _MAGIC:
        if content.startswith(magic):
            if mime == "image/webp" and content[8:12] != b"WEBP":
                continue
            return mime
    return None


def store(*, filename: str, content_type: str, content: bytes) -> str:
    if not content:
        raise BadRequest("文件内容为空")

    sniffed = _sniff(content)
    if sniffed is None:
        raise BadRequest("只支持 jpg / png / webp 格式的图片")
    if sniffed not in ALLOWED_CONTENT_TYPES:
        raise BadRequest("只支持 jpg / png / webp 格式的图片")
    if len(content) > MAX_BYTES:
        raise BadRequest("图片大小不能超过 5MB")

    if not (settings.cos_bucket and settings.cos_region and settings.cos_secret_id and settings.cos_secret_key):
        raise InternalError("服务端未配置对象存储（COS）")

    ext = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}[sniffed]
    key = f"dishes/{uuid.uuid4().hex}.{ext}"

    try:
        from qcloud_cos import CosConfig, CosS3Client

        client = CosS3Client(
            CosConfig(
                Region=settings.cos_region,
                SecretId=settings.cos_secret_id,
                SecretKey=settings.cos_secret_key,
            )
        )
        client.put_object(
            Bucket=settings.cos_bucket,
            Body=content,
            Key=key,
            ContentType=sniffed,
        )
    except Exception as exc:
        raise InternalError("图片上传失败，请稍后重试") from exc

    return to_absolute(key) or ""
