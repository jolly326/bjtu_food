"""运营端海报管理（docs/func/web/A4-海报管理.md、docs/api/web/posters.md）。

端点：
    GET    /admin/posters?canteenId=&stallId=&status=   分页（按 sort 升序，与学生端同序）
    POST   /admin/posters                               新建（sort 缺省排到最后）
    PUT    /admin/posters/{id}                          修改（sort 不传 = 保持原值）
    DELETE /admin/posters/{id}                          删除（COS 文件不回收）

图片上传复用 ``POST /admin/images``；删档口时海报由外键 ON DELETE CASCADE 连带清理。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.admin.poster import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import AdminPageResult, Envelope, ok, page_ok
from app.core.pagination import normalize_page
from app.core.schemas import InModel, OutModel

router = APIRouter(prefix="/admin/posters", tags=["admin-poster"])


class PosterSaveIn(InModel):
    stall_id: int
    image_url: str
    sort: int | None = None
    status: str = "on"


class AdminPosterOut(OutModel):
    id: int
    stall_id: int
    stall_name: str
    canteen_name: str
    image_url: str
    sort: int
    status: str
    updated_at: str


def _to_out(row) -> AdminPosterOut:
    return AdminPosterOut(
        id=row.id,
        stall_id=row.stall_id,
        stall_name=row.stall_name,
        canteen_name=row.canteen_name,
        image_url=row.image,
        sort=row.sort,
        status=row.status,
        updated_at=row.updated_at,
    )


@router.get("", response_model=Envelope[AdminPageResult[AdminPosterOut]], summary="海报列表")
def list_posters(
    stall_id: int | None = Query(default=None, alias="stallId"),
    canteen_id: int | None = Query(default=None, alias="canteenId"),
    status: str | None = Query(default=None),
    page: int | None = Query(default=None),
    page_size: int | None = Query(default=None, alias="pageSize"),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPageResult[AdminPosterOut]]:
    """按 ``sort`` 升序（同值按 ``id``）—— 与学生端橱窗同序。"""
    paging = normalize_page(page, page_size)
    rows, total = service.list_page(
        session,
        stall_id=stall_id,
        canteen_id=canteen_id,
        status=status,
        page=paging.page,
        size=paging.size,
    )
    return page_ok([_to_out(r) for r in rows], total=total)


@router.post("", response_model=Envelope[AdminPosterOut], summary="新建海报")
def create_poster(
    payload: PosterSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPosterOut]:
    return ok(_to_out(service.create(session, payload=payload)))


@router.put("/{poster_id}", response_model=Envelope[AdminPosterOut], summary="修改海报")
def update_poster(
    poster_id: int,
    payload: PosterSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPosterOut]:
    return ok(_to_out(service.update(session, poster_id=poster_id, payload=payload)))


@router.delete("/{poster_id}", response_model=Envelope[None], summary="删除海报")
def delete_poster(
    poster_id: int,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[None]:
    service.delete(session, poster_id=poster_id)
    return ok(None)