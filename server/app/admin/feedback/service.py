"""运营端反馈业务规则（docs/func/web/B-反馈与纠错/B1-反馈处理.md、docs/schema/feedback.md）。

* 列表默认全量，可按类型 / 处理状态筛
* 唯一动作 = 标记已处理（幂等）；不改正文、不给学生回执
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.feedback import repo
from app.admin.feedback.repo import FeedbackRow
from app.core.errors import NotFound
from app.models import Feedback


def list_page(
    session: Session,
    *,
    type_: str | None,
    handled: bool | None,
    page: int,
    size: int,
) -> tuple[list[FeedbackRow], int]:
    return repo.list_page(
        session, type_=type_, handled=handled, page=page, size=size
    )


def set_handled(session: Session, *, feedback_id: int) -> FeedbackRow:
    feedback = session.get(Feedback, feedback_id)
    if feedback is None:
        raise NotFound("反馈不存在")

    # 幂等：已处理则保持原时间；时间戳取数据库时钟
    if feedback.handled_at is None:
        feedback.handled_at = session.scalar(select(func.now()))
        session.flush()

    row = repo.find_row(session, feedback_id=feedback_id)
    if row is None:
        raise NotFound("反馈不存在")
    return row
