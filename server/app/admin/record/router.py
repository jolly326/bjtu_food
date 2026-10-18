"""运营端评分数据（只读）（docs/func/web/B1-反馈查看.md、docs/api/web/records.md）。

端点：
    GET /admin/dish-records?dishId=&reaction=&page=&pageSize=

🔴 只读：运营端不修改、不删除用户记录；不展示提交人标识（全匿名）。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.admin.record import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import AdminPageResult, Envelope, page_ok
from app.core.pagination import normalize_page
from app.core.schemas import OutModel

router = APIRouter(prefix="/admin", tags=["admin-record"])


class DishRecordOut(OutModel):
    id: int
    dish_name: str
    canteen_name: str
    stall_name: str
    reaction: int | None
    wanted_at: str
    reacted_at: str | None


@router.get("/dish-records", response_model=Envelope[AdminPageResult[DishRecordOut]], summary="评分数据")
def list_records(
    dish_id: int | None = Query(default=None, alias="dishId"),
    reaction: str | None = Query(default=None),
    page: int | None = Query(default=None),
    page_size: int | None = Query(default=None, alias="pageSize"),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPageResult[DishRecordOut]]:
    paging = normalize_page(page, page_size)
    rows, total = service.list_page(
        session,
        dish_id=dish_id,
        reaction=reaction,
        page=paging.page,
        size=paging.size,
    )
    return page_ok(
        [
            DishRecordOut(
                id=r.id,
                dish_name=r.dish_name,
                canteen_name=r.canteen_name,
                stall_name=r.stall_name,
                reaction=r.reaction,
                wanted_at=r.wanted_at,
                reacted_at=r.reacted_at,
            )
            for r in rows
        ],
        total=total,
    )
