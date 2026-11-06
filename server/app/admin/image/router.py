"""运营端图片上传（docs/api/web/images.md、docs/ui/web 档口管理 / 菜品管理）。

端点：
    POST /admin/images   multipart/form-data，字段 file

🔴 服务端校验类型白名单与体积上限，**不信任 Content-Type**。
🔴 学生端**没有**上传端点（无文字 / 图片 UGC）。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, File, UploadFile
from starlette.concurrency import run_in_threadpool

from app.admin.image import service
from app.core.deps import current_admin_id
from app.core.envelope import Envelope, ok
from app.core.errors import BadRequest
from app.core.schemas import OutModel

router = APIRouter(prefix="/admin", tags=["admin-image"])

_CHUNK_BYTES = 64 * 1024


async def _read_limited(file: UploadFile, limit: int) -> bytes:
    """分块读取并在超限时立即拒绝，避免整包进内存（无 Content-Length 的分块上传同样受限）。"""
    chunks: list[bytes] = []
    total = 0
    while True:
        chunk = await file.read(_CHUNK_BYTES)
        if not chunk:
            return b"".join(chunks)
        total += len(chunk)
        if total > limit:
            raise BadRequest("图片大小不能超过 5MB")
        chunks.append(chunk)


class ImageOut(OutModel):
    url: str


@router.post("/images", response_model=Envelope[ImageOut], summary="上传图片")
async def upload_image(
    file: UploadFile = File(...),
    admin_id: int = Depends(current_admin_id),
) -> Envelope[ImageOut]:
    content = await _read_limited(file, service.MAX_BYTES)
    # COS 上传是同步网络调用：丢线程池执行，避免阻塞整个事件循环
    url = await run_in_threadpool(service.store, content=content)
    return ok(ImageOut(url=url))
