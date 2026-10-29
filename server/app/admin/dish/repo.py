"""运营端菜品数据访问。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.formatters import format_dt
from app.core.storage import to_absolute
from app.models import Canteen, Dish, Stall


@dataclass(slots=True)
class DishRow:
    """菜品行 + 位置链路（image 为**绝对 URL**）。"""

    id: int
    stall_id: int
    stall_name: str
    canteen_name: str
    name: str
    image: str | None
    activate: bool
    like_count: int
    updated_at: str


def _to_row(dish: Dish, stall_name: str, canteen_name: str) -> DishRow:
    return DishRow(
        id=dish.id,
        stall_id=dish.stall_id,
        stall_name=stall_name,
        canteen_name=canteen_name,
        name=dish.name,
        image=to_absolute(dish.image),
        activate=dish.activate,
        like_count=dish.like_count,
        updated_at=format_dt(dish.updated_at) or "",
    )


def find_row(session: Session, *, dish_id: int) -> DishRow | None:
    stmt = (
        select(Dish, Stall.name, Canteen.name)
        .join(Stall, Stall.id == Dish.stall_id)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .where(Dish.id == dish_id)
    )
    found = session.execute(stmt).first()
    if found is None:
        return None
    dish, stall_name, canteen_name = found
    return _to_row(dish, stall_name, canteen_name)


def list_page(
    session: Session,
    *,
    stall_id: int | None,
    canteen_id: int | None,
    activate: bool | None,
    name: str | None,
    page: int,
    size: int,
) -> tuple[list[DishRow], int]:
    stmt = (
        select(Dish, Stall.name, Canteen.name)
        .join(Stall, Stall.id == Dish.stall_id)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .order_by(Dish.id)
    )
    if stall_id is not None:
        stmt = stmt.where(Dish.stall_id == stall_id)
    if canteen_id is not None:
        stmt = stmt.where(Stall.canteen_id == canteen_id)
    if activate is not None:
        stmt = stmt.where(Dish.activate == activate)
    if name:
        stmt = stmt.where(Dish.name.like(f"%{name}%"))

    count_stmt = select(func.count(Dish.id)).select_from(Dish).join(
        Stall, Stall.id == Dish.stall_id
    )
    if stall_id is not None:
        count_stmt = count_stmt.where(Dish.stall_id == stall_id)
    if canteen_id is not None:
        count_stmt = count_stmt.where(Stall.canteen_id == canteen_id)
    if activate is not None:
        count_stmt = count_stmt.where(Dish.activate == activate)
    if name:
        count_stmt = count_stmt.where(Dish.name.like(f"%{name}%"))
    total = session.scalar(count_stmt)

    pairs = session.execute(stmt.offset((page - 1) * size).limit(size)).all()
    return [_to_row(d, s, c) for d, s, c in pairs], int(total or 0)


def find_by_stall_name(
    session: Session, *, stall_id: int, name: str, exclude_id: int | None = None
) -> Dish | None:
    """同一档口下同名菜品；可排除自身（update 用）"""
    stmt = select(Dish).where(Dish.stall_id == stall_id, Dish.name == name)
    if exclude_id is not None:
        stmt = stmt.where(Dish.id != exclude_id)
    return session.scalar(stmt)

