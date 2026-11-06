"""运营端反馈业务规则（docs/func/web/B-反馈与纠错/B1-反馈处理.md、docs/schema/feedback.md）。

* 列表默认全量，可按类型 / 处理状态筛
* 唯一动作 = 标记已处理（幂等）；不改正文、不给学生回执
"""

from __future__ import annotations

from sqlalchemy import func, select, update
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
    exists = session.scalar(select(Feedback.id).where(Feedback.id == feedback_id))
    if exists is None:
        raise NotFound("反馈不存在")

    # 幂等 + 并发安全：条件更新只有「未处理」的行会被写入，
    # 两名管理员同时标记也只会落一次时间戳（时间取数据库时钟）
    session.execute(
        update(Feedback)
        .where(Feedback.id == feedback_id, Feedback.handled_at.is_(None))
        .values(handled_at=func.now())
    )
    session.commit()

    row = repo.find_row(session, feedback_id=feedback_id)
    if row is None:
        raise NotFound("反馈不存在")
    return row
