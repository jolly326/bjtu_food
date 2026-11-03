"""dish 域数据访问：在售菜品 + 售卖点（docs/schema/dish.md、dish_offering.md）。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Canteen, Dish, DishOffering, Stall


@dataclass(slots=True)
class OfferingRow:
    canteen_name: str
    stall_name: str
    floor: str
    price: int | None


@dataclass(slots=True)
class DishRow:
    id: int
    name: str
    image: str | None
    description: str
    embedding: list[float] | None
    locations: list[OfferingRow]


def list_on_sale(session: Session) -> list[DishRow]:
    """全部在售菜品 + 其**在售**售卖点（无档口时档口名 / 楼层用空串）。"""
    dishes = list(
        session.scalars(select(Dish).where(Dish.activate == True).order_by(Dish.id))
    )
    if not dishes:
        return []

    dish_ids = [d.id for d in dishes]
    offerings = session.execute(
        select(
            DishOffering,
            Canteen.name,
            func.coalesce(Stall.name, ""),
            func.coalesce(Stall.floor, ""),
        )
        .join(Canteen, Canteen.id == DishOffering.canteen_id)
        .outerjoin(Stall, Stall.id == DishOffering.stall_id)
        .where(DishOffering.dish_id.in_(dish_ids), DishOffering.activate == True)
        .order_by(DishOffering.id)
    ).all()

    by_dish: dict[int, list[OfferingRow]] = {}
    for offering, canteen_name, stall_name, floor in offerings:
        by_dish.setdefault(offering.dish_id, []).append(
            OfferingRow(
                canteen_name=canteen_name,
                stall_name=stall_name,
                floor=floor,
                price=offering.price,
            )
        )

    return [
        DishRow(
            id=d.id,
            name=d.name,
            image=d.image,
            description=d.description or "",
            embedding=d.embedding,
            locations=by_dish.get(d.id, []),
        )
        for d in dishes
    ]
