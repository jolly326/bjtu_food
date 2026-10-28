"""运营端档口数据访问。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.formatters import format_dt
from app.models import Canteen, Dish, Stall


@dataclass(slots=True)
class StallRow:
    """档口行 + 位置 + 统计（菜品数）。"""

    id: int
    canteen_id: int
    canteen_name: str
    name: str
    floor: str | None
    window_no: str | None
    dish_count: int
    updated_at: str


def _to_row(stall: Stall, canteen_name: str, dish_count: int) -> StallRow:
    return StallRow(
        id=stall.id,
        canteen_id=stall.canteen_id,
        canteen_name=canteen_name,
        name=stall.name,
        floor=stall.floor,
        window_no=stall.window_no,
        dish_count=dish_count,
        updated_at=format_dt(stall.updated_at) or "",
    )


def find_row(session: Session, *, stall_id: int) -> StallRow | None:
    stmt = (
        select(Stall, Canteen.name)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .where(Stall.id == stall_id)
    )
    found = session.execute(stmt).first()
    if found is None:
        return None
    stall, canteen_name = found
    dish_count = session.scalar(
        select(func.count()).select_from(Dish).where(Dish.stall_id == stall.id)
    )
    return _to_row(stall, canteen_name, int(dish_count or 0))


def has_same_name(
    session: Session, *, canteen_id: int, name: str, exclude_id: int | None = None
) -> bool:
    """同食堂同名是否已存在（``exclude_id`` 用于修改时排除自身）。"""
    stmt = select(Stall.id).where(Stall.canteen_id == canteen_id, Stall.name == name)
    if exclude_id is not None:
        stmt = stmt.where(Stall.id != exclude_id)
    return session.scalar(stmt) is not None


def list_page(
    session: Session, *, canteen_id: int | None, page: int, size: int
) -> tuple[list[StallRow], int]:
    stmt = (
        select(Stall, Canteen.name)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .order_by(Stall.id)
    )
    if canteen_id is not None:
        stmt = stmt.where(Stall.canteen_id == canteen_id)

    total = session.scalar(
        select(func.count()).select_from(stmt.order_by(None).subquery())
    )
    pairs = session.execute(stmt.offset((page - 1) * size).limit(size)).all()
    ids = [stall.id for stall, _ in pairs]
    dish_counts: dict[int, int] = {}
    if ids:
        dish_counts = dict(
            session.execute(
                select(Dish.stall_id, func.count())
                .where(Dish.stall_id.in_(ids))
                .group_by(Dish.stall_id)
            ).all()
        )
    return [
        _to_row(stall, canteen_name, int(dish_counts.get(stall.id, 0)))
        for stall, canteen_name in pairs
    ], int(total or 0)
