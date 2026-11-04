"""运营端菜品数据访问（docs/schema/dish.md、dish_location.md）。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.formatters import format_dt
from app.core.storage import to_absolute
from app.models import Canteen, Dish, DishLocation, Stall


@dataclass(slots=True)
class LocationRow:
    id: int
    canteen_id: int
    canteen_name: str
    stall_id: int | None
    stall_name: str
    floor: str
    activate: bool


@dataclass(slots=True)
class DishRow:
    id: int
    name: str
    description: str
    image: str | None
    price: int | None
    updated_at: str
    locations: list[LocationRow]


def _locations_for(session: Session, dish_ids: list[int]) -> dict[int, list[LocationRow]]:
    if not dish_ids:
        return {}
    rows = session.execute(
        select(
            DishLocation,
            Canteen.name,
            func.coalesce(Stall.name, ""),
            func.coalesce(Stall.floor, ""),
        )
        .join(Canteen, Canteen.id == DishLocation.canteen_id)
        .outerjoin(Stall, Stall.id == DishLocation.stall_id)
        .where(DishLocation.dish_id.in_(dish_ids))
        .order_by(DishLocation.id)
    ).all()
    grouped: dict[int, list[LocationRow]] = {}
    for location, canteen_name, stall_name, floor in rows:
        grouped.setdefault(location.dish_id, []).append(
            LocationRow(
                id=location.id,
                canteen_id=location.canteen_id,
                canteen_name=canteen_name,
                stall_id=location.stall_id,
                stall_name=stall_name,
                floor=floor,
                activate=location.activate,
            )
        )
    return grouped


def _to_row(dish: Dish, locations: list[LocationRow]) -> DishRow:
    return DishRow(
        id=dish.id,
        name=dish.name,
        description=dish.description or "",
        image=to_absolute(dish.image),
        price=dish.price,
        updated_at=format_dt(dish.updated_at) or "",
        locations=locations,
    )


def find_row(session: Session, *, dish_id: int) -> DishRow | None:
    dish = session.get(Dish, dish_id)
    if dish is None:
        return None
    return _to_row(dish, _locations_for(session, [dish_id]).get(dish_id, []))


def list_page(
    session: Session,
    *,
    canteen_id: int | None,
    stall_id: int | None,
    activate: bool | None,
    name: str | None,
    page: int,
    size: int,
) -> tuple[list[DishRow], int]:
    """列表口径：菜品本体，按位置归属 / 状态 / 名称过滤。"""
    stmt = select(Dish).order_by(Dish.id)
    if name:
        stmt = stmt.where(Dish.name.like(f"%{name}%"))
    if canteen_id is not None:
        stmt = stmt.where(
            Dish.id.in_(select(DishLocation.dish_id).where(DishLocation.canteen_id == canteen_id))
        )
    if stall_id is not None:
        stmt = stmt.where(
            Dish.id.in_(select(DishLocation.dish_id).where(DishLocation.stall_id == stall_id))
        )
    if activate is not None:
        stmt = stmt.where(
            Dish.id.in_(select(DishLocation.dish_id).where(DishLocation.activate == activate))
        )

    total = session.scalar(select(func.count()).select_from(stmt.subquery()))
    dishes = list(session.scalars(stmt.offset((page - 1) * size).limit(size)))
    grouped = _locations_for(session, [d.id for d in dishes])
    return [_to_row(d, grouped.get(d.id, [])) for d in dishes], int(total or 0)
