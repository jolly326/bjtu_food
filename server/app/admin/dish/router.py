"""运营端菜品管理（docs/api/web/dishes.md）。

    GET    /admin/dishes?canteenId=&stallId=&activate=&name=   分页
    POST   /admin/dishes                                        新建（含位置）
    PUT    /admin/dishes/{id}                                   修改（位置整表替换）
    DELETE /admin/dishes/{id}                                   删除
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from pydantic import Field
from sqlalchemy.orm import Session

from app.admin.dish import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import AdminPageResult, Envelope, ok, page_ok
from app.core.pagination import normalize_page
from app.core.schemas import InModel, OutModel

router = APIRouter(prefix="/admin/dishes", tags=["admin-dish"])


class DishLocationIn(InModel):
    id: int | None = None
    canteen_id: int
    stall_id: int | None = None
    activate: bool = True


class DishSaveIn(InModel):
    """入参长度 / 数值上下界与库列定义对齐：越界在参数层即 `400`，不落到 MySQL 报错。"""

    name: str = Field(min_length=1, max_length=64)
    description: str | None = Field(default=None, max_length=500)
    image_url: str | None = Field(default=None, max_length=255)
    price: int | None = Field(default=None, ge=0, le=2_147_483_647)
    locations: list[DishLocationIn] = Field(default_factory=list)


class DishLocationOut(OutModel):
    id: int
    canteen_id: int
    canteen_name: str
    stall_id: int | None = None
    stall_name: str
    floor: str
    activate: bool


class DishOut(OutModel):
    id: int
    name: str
    description: str
    image_url: str | None = None
    price: int | None = None
    locations: list[DishLocationOut]
    updated_at: str


def _to_out(row) -> DishOut:
    return DishOut(
        id=row.id,
        name=row.name,
        description=row.description,
        image_url=row.image,
        price=row.price,
        locations=[
            DishLocationOut(
                id=loc.id,
                canteen_id=loc.canteen_id,
                canteen_name=loc.canteen_name,
                stall_id=loc.stall_id,
                stall_name=loc.stall_name,
                floor=loc.floor,
                activate=loc.activate,
            )
            for loc in row.locations
        ],
        updated_at=row.updated_at,
    )


@router.get("", response_model=Envelope[AdminPageResult[DishOut]], summary="菜品列表")
def list_dishes(
    canteen_id: int | None = Query(default=None, alias="canteenId"),
    stall_id: int | None = Query(default=None, alias="stallId"),
    activate: bool | None = Query(default=None),
    name: str | None = Query(default=None, min_length=1, max_length=64, description="名称模糊匹配"),
    page: int | None = Query(default=None),
    page_size: int | None = Query(default=None, alias="pageSize"),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPageResult[DishOut]]:
    paging = normalize_page(page, page_size)
    rows, total = service.list_page(
        session,
        canteen_id=canteen_id,
        stall_id=stall_id,
        activate=activate,
        name=name,
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
