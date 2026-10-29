"""运营端食堂管理（docs/func/web/A1-食堂管理.md、docs/api/web/canteens.md）。

端点：
    GET    /admin/canteens        分页
    POST   /admin/canteens        新建
    PUT    /admin/canteens/{id}   修改
    DELETE /admin/canteens/{id}   删除（有下属档口时拒绝）
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.admin.canteen import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import AdminPageResult, Envelope, ok, page_ok
from app.core.pagination import normalize_page
from app.core.schemas import InModel, OutModel

router = APIRouter(prefix="/admin/canteens", tags=["admin-canteen"])


class CanteenSaveIn(InModel):
    name: str
    activate: bool = True


class AdminCanteenOut(OutModel):
    id: int
    name: str
    activate: bool
    stall_count: int
    updated_at: str


@router.get("", response_model=Envelope[AdminPageResult[AdminCanteenOut]], summary="食堂列表")
def list_canteens(
    page: int | None = Query(default=None),
    page_size: int | None = Query(default=None, alias="pageSize"),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPageResult[AdminCanteenOut]]:
    paging = normalize_page(page, page_size)
    rows, total = service.list_page(session, page=paging.page, size=paging.size)
    return page_ok(
        [
            AdminCanteenOut(
                id=r.id, name=r.name, activate=r.activate, stall_count=r.stall_count, updated_at=r.updated_at
            )
            for r in rows
        ],
        total=total,
    )


@router.post("", response_model=Envelope[AdminCanteenOut], summary="新建食堂")
def create_canteen(
    payload: CanteenSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminCanteenOut]:
    return ok(service.create(session, name=payload.name, activate=payload.activate))


@router.put("/{canteen_id}", response_model=Envelope[AdminCanteenOut], summary="修改食堂")
def update_canteen(
    canteen_id: int,
    payload: CanteenSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminCanteenOut]:
    return ok(
        service.update(
            session,
            canteen_id=canteen_id,
            name=payload.name,
            activate=payload.activate,
        )
    )


@router.delete("/{canteen_id}", response_model=Envelope[None], summary="删除食堂")
def delete_canteen(
    canteen_id: int,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[None]:
    service.delete(session, canteen_id=canteen_id)
    return ok(None)
