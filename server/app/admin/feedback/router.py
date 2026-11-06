"""运营端反馈处理（docs/api/web/feedback.md）。

    GET   /admin/feedback?type=&handled=        分页
    PATCH /admin/feedback/{id}/handled          标记已处理（幂等）
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.admin.feedback import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import AdminPageResult, Envelope, ok, page_ok
from app.core.pagination import normalize_page
from app.core.schemas import OutModel

router = APIRouter(prefix="/admin", tags=["admin-feedback"])


class FeedbackOut(OutModel):
    id: int
    type: str
    dish_id: int | None = None
    dish_name: str
    content: str
    created_at: str
    handled_at: str | None = None


def _to_out(row) -> FeedbackOut:
    return FeedbackOut(
        id=row.id,
        type=row.type,
        dish_id=row.dish_id,
        dish_name=row.dish_name,
        content=row.content,
        created_at=row.created_at,
        handled_at=row.handled_at,
    )


@router.get(
    "/feedback",
    response_model=Envelope[AdminPageResult[FeedbackOut]],
    summary="反馈列表",
)
def list_feedback(
    type_: str | None = Query(default=None, alias="type"),
    handled: bool | None = Query(default=None),
    page: int | None = Query(default=None),
    page_size: int | None = Query(default=None, alias="pageSize"),
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[AdminPageResult[FeedbackOut]]:
    paging = normalize_page(page, page_size)
    rows, total = service.list_page(
        session, type_=type_, handled=handled, page=paging.page, size=paging.size
    )
    return page_ok([_to_out(r) for r in rows], total=total)


@router.patch(
    "/feedback/{feedback_id}/handled",
    response_model=Envelope[FeedbackOut],
    summary="标记已处理",
)
def mark_handled(
    feedback_id: int,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[FeedbackOut]:
    return ok(_to_out(service.set_handled(session, feedback_id=feedback_id)))
