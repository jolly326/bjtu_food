"""运营端菜品数据访问。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

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
    price: int
    image: str | None
    status: str
    avg_reaction: float | None
    reaction_count: int
    updated_at: str


def _to_row(dish: Dish, stall_name: str, canteen_name: str) -> DishRow:
    return DishRow(
        id=dish.id,
        stall_id=dish.stall_id,
        stall_name=stall_name,
        canteen_name=canteen_name,
        name=dish.name,
        price=dish.price,
        image=to_absolute(dish.image),
        status=dish.status,
        avg_reaction=float(dish.avg_reaction) if dish.avg_reaction is not None else None,
        reaction_count=dish.reaction_count,
        updated_at=dish.updated_at.strftime("%Y-%m-%d %H:%M:%S") if dish.updated_at else "",
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
    status: str | None,
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
    if status is not None:
        stmt = stmt.where(Dish.status == status)

    total = session.scalar(select(func.count()).select_from(stmt.subquery()))
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

