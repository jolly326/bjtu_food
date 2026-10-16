"""dish 域数据访问。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Canteen, Dish, DishRecord, Stall


@dataclass(slots=True)
class DishRow:
    """在售菜品 + 位置链路（食堂 / 档口）。"""

    id: int
    name: str
    price: int
    image: str | None
    avg_reaction: float | None
    canteen_name: str
    stall_name: str


def list_on_sale(session: Session, *, canteen_id: int | None = None) -> list[DishRow]:
    """在售菜品 + 位置；可选按食堂筛选。"""
    stmt = (
        select(Dish, Stall.name, Canteen.name)
        .join(Stall, Stall.id == Dish.stall_id)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .where(Dish.status == "on")
    )
    if canteen_id is not None:
        stmt = stmt.where(Stall.canteen_id == canteen_id)

    rows: list[DishRow] = []
    for dish, stall_name, canteen_name in session.execute(stmt):
        rows.append(
            DishRow(
                id=dish.id,
                name=dish.name,
                price=dish.price,
                image=dish.image,
                avg_reaction=float(dish.avg_reaction) if dish.avg_reaction is not None else None,
                canteen_name=canteen_name,
                stall_name=stall_name,
            )
        )
    return rows


def unrated_dish_ids(session: Session, *, student_id: int) -> set[int]:
    """当前用户**未评分**的菜品 id（决定 wanted 与按钮态）。"""
    stmt = select(DishRecord.dish_id).where(
        DishRecord.user_id == student_id,
        DishRecord.reaction.is_(None),
    )
    return set(session.scalars(stmt))
