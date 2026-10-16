"""运营端纠错处置（docs/func/web/B2-纠错查看.md、docs/api/web/issues.md）。

端点：
    GET   /admin/dish-issues?handled=&dishId=   分页（默认只查未处理）
    PATCH /admin/dish-issues/{id}                { handled: true|false }（可撤销误标）

🔴 本域只标记「看过了」—— **不修改菜品**、**不给学生回执**、**不删除提交行**。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.admin.issue import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import AdminPageResult, Envelope, ok, page_ok
from app.core.pagination import normalize_page
from app.core.schemas import InModel, OutModel

router = APIRouter(prefix="/admin", tags=["admin-issue"])


class HandleIn(InModel):
    handled: bool


class DishIssueOut(OutModel):
    id: int
    dish_id: int
    dish_name: str
    canteen_name: str
    stall_name: str
    option_id: int
    option_name: str
    created_at: str
    handled_at: str | None = None


@router.get("/dish-issues", response_model=Envelope[AdminPageResult[DishIssueOut]], summary="纠错列表")
def list_issues(
    handled: bool | None = Query(default=False),
    dish_id: int | None = Query(default=None),
    page: int | None = Query(default=None),
    page_size: int | None = Query(default=None, alias="pageSize"),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPageResult[DishIssueOut]]:
    paging = normalize_page(page, page_size)
    rows, total = service.list_page(
        session, handled=handled, dish_id=dish_id, page=paging.page, size=paging.size
    )
    return page_ok(
        [
            DishIssueOut(
                id=r.id,
                dish_id=r.dish_id,
                dish_name=r.dish_name,
                canteen_name=r.canteen_name,
                stall_name=r.stall_name,
                option_id=r.option_id,
                option_name=r.option_name,
                created_at=r.created_at,
                handled_at=r.handled_at,
            )
            for r in rows
        ],
        total=total,
    )


@router.patch("/dish-issues/{issue_id}", response_model=Envelope[DishIssueOut], summary="标记已处理")
def handle_issue(
    issue_id: int,
    payload: HandleIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[DishIssueOut]:
    return ok(service.set_handled(session, issue_id=issue_id, handled=payload.handled))
