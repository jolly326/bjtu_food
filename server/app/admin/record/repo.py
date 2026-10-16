"""运营端评分数据仓储。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Canteen, Dish, DishRecord, Stall


@dataclass(slots=True)
class RecordRow:
    id: int
    dish_id: int
    dish_name: str
    canteen_name: str
    stall_name: str
    reaction: int | None
    wanted_at: str
    reacted_at: str | None


def _fmt(value) -> str | None:
    return value.strftime("%Y-%m-%d %H:%M:%S") if value else None


def _to_row(record: DishRecord, dish_name: str, canteen_name: str, stall_name: str) -> RecordRow:
    return RecordRow(
        id=record.id,
        dish_id=record.dish_id,
        dish_name=dish_name,
        canteen_name=canteen_name,
        stall_name=stall_name,
        reaction=record.reaction,
        wanted_at=_fmt(record.wanted_at) or "",
        reacted_at=_fmt(record.reacted_at),
    )


def list_page(
    session: Session,
    *,
    dish_id: int | None,
    reaction: str | None,
    page: int,
    size: int,
) -> tuple[list[RecordRow], int]:
    stmt = (
        select(DishRecord, Dish.name, Canteen.name, Stall.name)
        .join(Dish, Dish.id == DishRecord.dish_id)
        .join(Stall, Stall.id == Dish.stall_id)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .order_by(DishRecord.wanted_at.desc())
    )
    if dish_id is not None:
        stmt = stmt.where(DishRecord.dish_id == dish_id)
    if reaction == "none":
        stmt = stmt.where(DishRecord.reaction.is_(None))
    elif reaction is not None:
        stmt = stmt.where(DishRecord.reaction == int(reaction))

    total = session.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = session.execute(stmt.offset((page - 1) * size).limit(size)).all()
    return [_to_row(r, d, c, s) for r, d, c, s in rows], int(total or 0)
