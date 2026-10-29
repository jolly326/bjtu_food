"""issue 域数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import DishIssue, DishIssueOption


def list_options(session: Session) -> list[DishIssueOption]:
    stmt = select(DishIssueOption).order_by(DishIssueOption.id)
    return list(session.scalars(stmt))


def exists_any(
    session: Session, *, student_id: int, dish_id: int, option_id: int
) -> bool:
    """同人 + 同菜 + 同选项是否存在任意提交记录（仅用于统计，不做去重）。"""
    stmt = (
        select(DishIssue.id)
        .where(
            DishIssue.user_id == student_id,
            DishIssue.dish_id == dish_id,
            DishIssue.option_id == option_id,
        )
        .limit(1)
    )
    return session.scalar(stmt) is not None
