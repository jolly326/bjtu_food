"""dish 域业务规则（docs/func/client/A2-美味.md、docs/schema/dish.md）。"""

from __future__ import annotations

import random
from typing import TypeVar

from sqlalchemy.orm import Session

from app.core.errors import BadRequest
from app.core.rating import flavor_level
from app.core.schemas import OutModel
from app.core.storage import to_absolute
from app.models import Canteen
from app.student.dish import repo

# 零评分菜的基线权重：不因缺失评价分而获得高分，也不被排除
BASE_WEIGHT = 1.0

T = TypeVar("T")


class DishCardOut(OutModel):
    """推荐卡片出参（docs/api/client/dishes.md 的 DishCardVO）。"""

    id: int
    name: str
    price: int
    image_url: str | None
    flavor_level: str | None
    wanted: bool
    canteen_name: str
    stall_name: str


def weight_of(avg_reaction: float | None) -> float:
    """评价越好权重越高；零评分走基线权重。"""
    if avg_reaction is None:
        return BASE_WEIGHT
    return BASE_WEIGHT + max(0.0, float(avg_reaction))


def _weighted_sample[T](items: list[tuple[T, float]], limit: int) -> list[T]:
    """不放回加权抽样（Efraimidis-Spirakis：key = random() ** (1/w)）。"""
    if limit <= 0:
        return []
    keyed = [(random.random() ** (1.0 / w), item) for item, w in items if w > 0]
    keyed.sort(key=lambda pair: pair[0], reverse=True)
    return [item for _, item in keyed[:limit]]


def sample_cards(
    session: Session,
    *,
    student_id: int,
    canteen_id: int | None,
    limit: int,
) -> list[DishCardOut]:
    """在售菜品按评价分加权随机取样，附带本人 wanted 态。"""
    if canteen_id is not None and session.get(Canteen, canteen_id) is None:
        raise BadRequest("食堂不存在")

    rows = repo.list_on_sale(session, canteen_id=canteen_id)
    if not rows:
        return []

    wanted_ids = repo.unrated_dish_ids(session, student_id=student_id)
    picked = _weighted_sample([(row, weight_of(row.avg_reaction)) for row in rows], limit)

    return [
        DishCardOut(
            id=row.id,
            name=row.name,
            price=row.price,
            image_url=to_absolute(row.image),
            flavor_level=flavor_level(row.avg_reaction, row.reaction_count),
            wanted=row.id in wanted_ids,
            canteen_name=row.canteen_name,
            stall_name=row.stall_name,
        )
        for row in picked
    ]
