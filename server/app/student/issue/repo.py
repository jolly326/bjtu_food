"""issue 域数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import DishIssue, DishIssueOption


def list_options(session: Session) -> list[DishIssueOption]:
    stmt = select(DishIssueOption).order_by(DishIssueOption.id)
    return list(session.scalars(stmt))


def find_unhandled(
    session: Session, *, student_id: int, dish_id: int, option_id: int
) -> DishIssue | None:
    """同人 + 同菜 + 同选项的**未处理**记录（用于幂等）。"""
    stmt = (
        select(DishIssue)
        .where(
            DishIssue.user_id == student_id,
            DishIssue.dish_id == dish_id,
            DishIssue.option_id == option_id,
            DishIssue.handled_at.is_(None),
        )
        .limit(1)
    )
    return session.scalar(stmt)
