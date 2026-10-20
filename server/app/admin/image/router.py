"""运营端图片上传（docs/api/web/images.md、docs/ui/web 档口管理 / 菜品管理）。

端点：
    POST /admin/images   multipart/form-data，字段 file

🔴 服务端校验类型白名单与体积上限，**不信任 Content-Type**。
🔴 学生端**没有**上传端点（无文字 / 图片 UGC）。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, File, UploadFile
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from app.admin.image import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import Envelope, ok
from app.core.schemas import OutModel

router = APIRouter(prefix="/admin", tags=["admin-image"])

MAX_UPLOAD_BYTES = 5 * 1024 * 1024  # 5MB


class ImageOut(OutModel):
    url: str


@router.post("/images", response_model=Envelope[ImageOut], summary="上传图片")
async def upload_image(
    file: UploadFile = File(...),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[ImageOut]:
    content = await file.read()
    # COS 上传是同步网络调用：丢线程池执行，避免阻塞整个事件循环
    url = await run_in_threadpool(
        service.store,
        filename=file.filename or "",
        content_type=file.content_type or "",
        content=content,
    )
    return ok(ImageOut(url=url))
