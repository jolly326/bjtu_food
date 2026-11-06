"""dish 域数据访问：在售位置 → 菜品本体（docs/schema/dish.md、dish_location.md）。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Canteen, Dish, DishEmbedding, DishLocation, Stall


@dataclass(slots=True)
class LocationRow:
    dish_id: int
    location_id: int
    name: str
    description: str
    image: str | None
    price: int | None
    embedding: list[float] | None
    canteen_name: str
    floor: str
    stall_name: str


def list_on_sale(session: Session, *, model: str) -> list[LocationRow]:
    """全部在售位置（一个位置一行；向量只取当前模型）。"""
    rows = session.execute(
        select(
            Dish,
            DishLocation,
            Canteen.name,
            func.coalesce(Stall.floor, ""),
            func.coalesce(Stall.name, ""),
            DishEmbedding.vector,
        )
        .join(DishLocation, DishLocation.dish_id == Dish.id)
        .join(Canteen, Canteen.id == DishLocation.canteen_id)
        .outerjoin(Stall, Stall.id == DishLocation.stall_id)
        .outerjoin(
            DishEmbedding,
            (DishEmbedding.dish_id == Dish.id) & (DishEmbedding.model == model),
        )
        .where(DishLocation.activate.is_(True))
        .order_by(Dish.id, DishLocation.id)
    ).all()

    return [
        LocationRow(
            dish_id=dish.id,
            location_id=location.id,
            name=dish.name,
            description=dish.description or "",
            image=dish.image,
            price=dish.price,
            embedding=vector,
            canteen_name=canteen_name,
            floor=floor,
            stall_name=stall_name,
        )
        for dish, location, canteen_name, floor, stall_name, vector in rows
    ]
