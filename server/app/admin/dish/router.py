"""运营端菜品管理（docs/func/web/A3-菜品管理.md、docs/api/web/dishes.md）。

端点：
    GET    /admin/dishes?stallId=&canteenId=&status=   分页
    POST   /admin/dishes                               新建
    PUT    /admin/dishes/{id}                          修改
    DELETE /admin/dishes/{id}                          删除（连带清理记录与纠错）

🔴 ``likeCount`` 是**派生缓存列**，只读出参、不可写入。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.admin.dish import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import AdminPageResult, Envelope, ok, page_ok
from app.core.pagination import normalize_page
from app.core.schemas import InModel, OutModel

router = APIRouter(prefix="/admin/dishes", tags=["admin-dish"])


class DishSaveIn(InModel):
    stall_id: int
    name: str
    image_url: str | None = None
    status: str = "on"


class DishOut(OutModel):
    id: int
    stall_id: int
    stall_name: str
    canteen_name: str
    name: str
    image_url: str | None = None
    status: str
    like_count: int
    updated_at: str


def _to_out(row) -> DishOut:
    return DishOut(
        id=row.id,
        stall_id=row.stall_id,
        stall_name=row.stall_name,
        canteen_name=row.canteen_name,
        name=row.name,
        image_url=row.image,
        status=row.status,
        like_count=row.like_count,
        updated_at=row.updated_at,
    )


@router.get("", response_model=Envelope[AdminPageResult[DishOut]], summary="菜品列表")
def list_dishes(
    stall_id: int | None = Query(default=None, alias="stallId"),
    canteen_id: int | None = Query(default=None, alias="canteenId"),
    status: str | None = Query(default=None),
    page: int | None = Query(default=None),
    page_size: int | None = Query(default=None, alias="pageSize"),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPageResult[DishOut]]:
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


@router.post("", response_model=Envelope[DishOut], summary="新建菜品")
def create_dish(
    payload: DishSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[DishOut]:
    return ok(_to_out(service.create(session, payload=payload)))


@router.put("/{dish_id}", response_model=Envelope[DishOut], summary="修改菜品")
def update_dish(
    dish_id: int,
    payload: DishSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[DishOut]:
    return ok(_to_out(service.update(session, dish_id=dish_id, payload=payload)))


@router.delete("/{dish_id}", response_model=Envelope[None], summary="删除菜品")
def delete_dish(
    dish_id: int,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[None]:
    service.delete(session, dish_id=dish_id)
    return ok(None)
