"""运营端档口管理（docs/func/web/A2-档口管理.md、docs/api/web/stalls.md）。

端点：
    GET    /admin/stalls?canteenId=   分页
    POST   /admin/stalls               新建
    PUT    /admin/stalls/{id}          修改
    DELETE /admin/stalls/{id}          删除（有下属菜品时拒绝；海报级联清理）
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.admin.stall import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import AdminPageResult, Envelope, ok, page_ok
from app.core.pagination import normalize_page
from app.core.schemas import InModel, OutModel

router = APIRouter(prefix="/admin/stalls", tags=["admin-stall"])


class StallSaveIn(InModel):
    canteen_id: int
    name: str
    floor: str | None = None
    window_no: str | None = None


class StallOut(OutModel):
    id: int
    canteen_id: int
    canteen_name: str
    name: str
    floor: str | None = None
    window_no: str | None = None
    dish_count: int
    poster_count: int
    updated_at: str


def _to_out(row) -> StallOut:
    return StallOut(
        id=row.id,
        canteen_id=row.canteen_id,
        canteen_name=row.canteen_name,
        name=row.name,
        floor=row.floor,
        window_no=row.window_no,
        dish_count=row.dish_count,
        poster_count=row.poster_count,
        updated_at=row.updated_at,
    )


@router.get("", response_model=Envelope[AdminPageResult[StallOut]], summary="档口列表")
def list_stalls(
    canteen_id: int | None = Query(default=None, alias="canteenId"),
    page: int | None = Query(default=None),
    page_size: int | None = Query(default=None, alias="pageSize"),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPageResult[StallOut]]:
    paging = normalize_page(page, page_size)
    rows, total = service.list_page(
        session, canteen_id=canteen_id, page=paging.page, size=paging.size
    )
    return page_ok([_to_out(r) for r in rows], total=total)


@router.post("", response_model=Envelope[StallOut], summary="新建档口")
def create_stall(
    payload: StallSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[StallOut]:
    return ok(_to_out(service.create(session, payload=payload)))


@router.put("/{stall_id}", response_model=Envelope[StallOut], summary="修改档口")
def update_stall(
    stall_id: int,
    payload: StallSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[StallOut]:
    return ok(_to_out(service.update(session, stall_id=stall_id, payload=payload)))


@router.delete("/{stall_id}", response_model=Envelope[None], summary="删除档口")
def delete_stall(
    stall_id: int,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[None]:
    service.delete(session, stall_id=stall_id)
    return ok(None)
