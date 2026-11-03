"""运营端菜品数据访问（docs/schema/dish.md、dish_offering.md）。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.formatters import format_dt
from app.core.storage import to_absolute
from app.models import Canteen, Dish, DishOffering, Stall


@dataclass(slots=True)
class OfferingRow:
    id: int
    canteen_id: int
    canteen_name: str
    stall_id: int | None
    stall_name: str
    floor: str
    price: int | None
    activate: bool


@dataclass(slots=True)
class DishRow:
    id: int
    name: str
    description: str
    image: str | None
    activate: bool
    updated_at: str
    offerings: list[OfferingRow]


def _offerings_for(session: Session, dish_ids: list[int]) -> dict[int, list[OfferingRow]]:
    if not dish_ids:
        return {}
    rows = session.execute(
        select(
            DishOffering,
            Canteen.name,
            func.coalesce(Stall.name, ""),
            func.coalesce(Stall.floor, ""),
        )
        .join(Canteen, Canteen.id == DishOffering.canteen_id)
        .outerjoin(Stall, Stall.id == DishOffering.stall_id)
        .where(DishOffering.dish_id.in_(dish_ids))
        .order_by(DishOffering.id)
    ).all()

    grouped: dict[int, list[OfferingRow]] = {}
    for offering, canteen_name, stall_name, floor in rows:
        grouped.setdefault(offering.dish_id, []).append(
            OfferingRow(
                id=offering.id,
                canteen_id=offering.canteen_id,
                canteen_name=canteen_name,
                stall_id=offering.stall_id,
                stall_name=stall_name,
                floor=floor,
                price=offering.price,
                activate=offering.activate,
            )
        )
    return grouped


def _to_row(dish: Dish, offerings: list[OfferingRow]) -> DishRow:
    return DishRow(
        id=dish.id,
        name=dish.name,
        description=dish.description or "",
        image=to_absolute(dish.image),
        activate=dish.activate,
        updated_at=format_dt(dish.updated_at) or "",
        offerings=offerings,
    )


def find_row(session: Session, *, dish_id: int) -> DishRow | None:
    dish = session.get(Dish, dish_id)
    if dish is None:
        return None
    return _to_row(dish, _offerings_for(session, [dish_id]).get(dish_id, []))


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
    stmt = select(Dish).order_by(Dish.id)
    if activate is not None:
        stmt = stmt.where(Dish.activate == activate)
    if name:
        stmt = stmt.where(Dish.name.like(f"%{name}%"))
    if canteen_id is not None:
        stmt = stmt.where(
            Dish.id.in_(select(DishOffering.dish_id).where(DishOffering.canteen_id == canteen_id))
        )
    if stall_id is not None:
        stmt = stmt.where(
            Dish.id.in_(select(DishOffering.dish_id).where(DishOffering.stall_id == stall_id))
        )

    total = session.scalar(select(func.count()).select_from(stmt.subquery()))
    dishes = list(session.scalars(stmt.offset((page - 1) * size).limit(size)))
    grouped = _offerings_for(session, [d.id for d in dishes])
    return [_to_row(d, grouped.get(d.id, [])) for d in dishes], int(total or 0)


def find_by_name(session: Session, *, name: str, exclude_id: int | None = None) -> Dish | None:
    """全局同名菜品；``exclude_id`` 用于修改时排除自身。"""
    stmt = select(Dish).where(Dish.name == name)
    if exclude_id is not None:
        stmt = stmt.where(Dish.id != exclude_id)
    return session.scalar(stmt)
