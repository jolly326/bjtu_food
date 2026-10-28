"""运营端纠错处置数据访问。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import cast

from sqlalchemy import func, select
from sqlalchemy.orm import Session

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


def _to_row(
    issue: DishIssue, dish_name: str, canteen_name: str, stall_name: str, option_name: str
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
    )


def _base_query():
    return (
        select(DishIssue, Dish.name, Canteen.name, Stall.name, DishIssueOption.name)
        .join(Dish, Dish.id == DishIssue.dish_id)
        .join(Stall, Stall.id == Dish.stall_id)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .join(DishIssueOption, DishIssueOption.id == DishIssue.option_id)
    )


def find_row(session: Session, *, issue_id: int) -> IssueRow | None:
    found = session.execute(_base_query().where(DishIssue.id == issue_id)).first()
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
    stmt = _base_query().order_by(DishIssue.created_at.desc())
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
