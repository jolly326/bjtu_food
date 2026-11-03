"""运营端反馈数据访问（docs/schema/feedback.md）。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.formatters import format_dt
from app.models import Dish, Feedback


@dataclass(slots=True)
class FeedbackRow:
    id: int
    type: str
    dish_id: int | None
    dish_name: str
    content: str
    created_at: str
    handled_at: str | None


def _to_row(feedback: Feedback, dish_name: str) -> FeedbackRow:
    return FeedbackRow(
        id=feedback.id,
        type=feedback.type,
        dish_id=feedback.dish_id,
        dish_name=dish_name,
        content=feedback.content,
        created_at=format_dt(feedback.created_at) or "",
        handled_at=format_dt(feedback.handled_at),
    )


def _base():
    return select(Feedback, func.coalesce(Dish.name, "")).outerjoin(
        Dish, Dish.id == Feedback.dish_id
    )


def find_row(session: Session, *, feedback_id: int) -> FeedbackRow | None:
    found = session.execute(_base().where(Feedback.id == feedback_id)).first()
    if found is None:
        return None
    feedback, dish_name = found
    return _to_row(feedback, dish_name)


def list_page(
    session: Session,
    *,
    type_: str | None,
    handled: bool | None,
    page: int,
    size: int,
) -> tuple[list[FeedbackRow], int]:
    stmt = _base().order_by(
        Feedback.handled_at.is_(None).desc(), Feedback.created_at.desc()
    )
    count_stmt = select(func.count(Feedback.id))
    if type_:
        stmt = stmt.where(Feedback.type == type_)
        count_stmt = count_stmt.where(Feedback.type == type_)
    if handled is not None:
        cond = (
            Feedback.handled_at.is_not(None) if handled else Feedback.handled_at.is_(None)
        )
        stmt = stmt.where(cond)
        count_stmt = count_stmt.where(cond)

    total = session.scalar(count_stmt)
    rows = session.execute(stmt.offset((page - 1) * size).limit(size)).all()
    return [_to_row(fb, dish_name) for fb, dish_name in rows], int(total or 0)
