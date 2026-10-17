"""运营端档口数据访问。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Canteen, Dish, Poster, Stall


@dataclass(slots=True)
class StallRow:
    """档口行 + 位置 + 统计（菜品数 / 海报数）。"""

    id: int
    canteen_id: int
    canteen_name: str
    name: str
    floor: str | None
    window_no: str | None
    dish_count: int
    poster_count: int
    updated_at: str


def _to_row(stall: Stall, canteen_name: str, dish_count: int, poster_count: int) -> StallRow:
    return StallRow(
        id=stall.id,
        canteen_id=stall.canteen_id,
        canteen_name=canteen_name,
        name=stall.name,
        floor=stall.floor,
        window_no=stall.window_no,
        dish_count=dish_count,
        poster_count=poster_count,
        updated_at=stall.updated_at.strftime("%Y-%m-%d %H:%M:%S") if stall.updated_at else "",
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
    poster_count = session.scalar(
        select(func.count()).select_from(Poster).where(Poster.stall_id == stall.id)
    )
    return _to_row(stall, canteen_name, int(dish_count or 0), int(poster_count or 0))


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

    dish_counts = dict(
        session.execute(select(Dish.stall_id, func.count()).group_by(Dish.stall_id)).all()
    )
    poster_counts = dict(
        session.execute(select(Poster.stall_id, func.count()).group_by(Poster.stall_id)).all()
    )
    return [
        _to_row(
            stall,
            canteen_name,
            int(dish_counts.get(stall.id, 0)),
            int(poster_counts.get(stall.id, 0)),
        )
        for stall, canteen_name in pairs
    ], int(total or 0)
