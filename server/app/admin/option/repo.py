"""运营端纠错选项数据访问。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.formatters import format_dt
from app.models import DishIssue, DishIssueOption


@dataclass(slots=True)
class OptionRow:
    id: int
    name: str
    ref_count: int
    updated_at: str


def _to_row(option: DishIssueOption, ref_count: int) -> OptionRow:
    return OptionRow(
        id=option.id,
        name=option.name,
        ref_count=ref_count,
        updated_at=format_dt(option.updated_at) or "",
    )


def find_row(session: Session, *, option_id: int) -> OptionRow | None:
    option = session.get(DishIssueOption, option_id)
    if option is None:
        return None
    ref_count = session.scalar(
        select(func.count()).select_from(DishIssue).where(DishIssue.option_id == option.id)
    )
    return _to_row(option, int(ref_count or 0))


def list_all(session: Session) -> list[OptionRow]:
    options = list(session.scalars(select(DishIssueOption).order_by(DishIssueOption.id)))
    ids = [o.id for o in options]
    counts: dict[int, int] = {}
    if ids:
        counts = dict(
            session.execute(
                select(DishIssue.option_id, func.count())
                .where(DishIssue.option_id.in_(ids))
                .group_by(DishIssue.option_id)
            ).all()
        )
    return [_to_row(o, int(counts.get(o.id, 0))) for o in options]
