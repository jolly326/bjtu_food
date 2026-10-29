"""运营端纠错处置数据访问。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import cast

from sqlalchemy import func, select
from sqlalchemy.orm import Session, aliased

from app.core.formatters import format_dt
from app.models import Canteen, Dish, DishIssue, DishIssueOption, Stall


@dataclass(slots=True)
class IssueRow:
    id: int
    dish_id: int
    dish_name: str
    canteen_name: str
    stall_name: str
    option_id: int
    option_name: str
    created_at: str
    handled_at: str | None
    dish_feedback_count: int


def _to_row(
    issue: DishIssue,
    dish_name: str,
    canteen_name: str,
    stall_name: str,
    option_name: str,
    dish_feedback_count: int,
) -> IssueRow:
    return IssueRow(
        id=issue.id,
        dish_id=issue.dish_id,
        dish_name=dish_name,
        canteen_name=canteen_name,
        stall_name=stall_name,
        option_id=issue.option_id,
        option_name=option_name,
        created_at=format_dt(issue.created_at) or "",
        handled_at=format_dt(issue.handled_at),
        dish_feedback_count=int(dish_feedback_count or 0),
    )


def _base_query():
    """主查询 + 「该菜当前待办条数」标量子查询（排序与出参共用）。"""
    pending = aliased(DishIssue)
    feedback_count = (
        select(func.count(pending.id))
        .where(pending.dish_id == DishIssue.dish_id, pending.handled_at.is_(None))
        .correlate(DishIssue)
        .scalar_subquery()
    )
    stmt = (
        select(
            DishIssue,
            Dish.name,
            Canteen.name,
            Stall.name,
            DishIssueOption.name,
            feedback_count,
        )
        .join(Dish, Dish.id == DishIssue.dish_id)
        .join(Stall, Stall.id == Dish.stall_id)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .join(DishIssueOption, DishIssueOption.id == DishIssue.option_id)
    )
    return stmt, feedback_count


def find_row(session: Session, *, issue_id: int) -> IssueRow | None:
    stmt, _ = _base_query()
    found = session.execute(stmt.where(DishIssue.id == issue_id)).first()
    if found is None:
        return None
    return _to_row(*cast("tuple", found))


def list_page(
    session: Session,
    *,
    handled: bool | None,
    dish_id: int | None,
    page: int,
    size: int,
) -> tuple[list[IssueRow], int]:
    stmt, feedback_count = _base_query()
    # 排序 = 该菜当前待办条数降序 → 提交时间降序（被多人反馈的菜顶到最前）
    stmt = stmt.order_by(feedback_count.desc(), DishIssue.created_at.desc())
    if handled is not None:
        stmt = stmt.where(
            DishIssue.handled_at.is_(None) if not handled else DishIssue.handled_at.is_not(None)
        )
    if dish_id is not None:
        stmt = stmt.where(DishIssue.dish_id == dish_id)

    count_stmt = select(func.count(DishIssue.id))
    if handled is not None:
        count_stmt = count_stmt.where(
            DishIssue.handled_at.is_(None) if not handled else DishIssue.handled_at.is_not(None)
        )
    if dish_id is not None:
        count_stmt = count_stmt.where(DishIssue.dish_id == dish_id)
    total = session.scalar(count_stmt)

    rows = session.execute(stmt.offset((page - 1) * size).limit(size)).all()
    return [_to_row(*cast("tuple", row)) for row in rows], int(total or 0)
