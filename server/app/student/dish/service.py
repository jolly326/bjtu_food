"""dish 域业务规则（docs/func/client/A1-美食地图.md、docs/schema/dish.md、dish_rating.md）。"""

from __future__ import annotations

from sqlalchemy.orm import Session

from app.core.errors import NotFound
from app.core.schemas import OutModel
from app.core.storage import to_absolute
from app.models import Dish
from app.student.dish import repo


class DishVO(OutModel):
    """美食地图磁贴出参（docs/api/client/dishes.md 的 DishVO）。"""

    id: int
    name: str
    price: int
    image_url: str | None
    like_count: int
    liked: bool
    canteen_name: str
    stall_name: str


class DishLikeOut(OutModel):
    """点赞 toggle 出参（docs/api/client/dishes.md）。"""

    id: int
    like_count: int
    liked: bool


def list_for_map(session: Session, *, student_id: int) -> list[DishVO]:
    """美食地图全量在售菜品，附带本人点赞态。"""
    rows = repo.list_on_sale(session)
    liked_ids = repo.liked_dish_ids(session, student_id=student_id)
    return [
        DishVO(
            id=row.id,
            name=row.name,
            price=row.price,
            image_url=to_absolute(row.image),
            like_count=row.like_count,
            liked=row.id in liked_ids,
            canteen_name=row.canteen_name,
            stall_name=row.stall_name,
        )
        for row in rows
    ]


def toggle_like(session: Session, *, student_id: int, dish_id: int) -> DishLikeOut:
    """两态点赞 toggle（docs/func/client/B1-点赞反馈.md）。"""
    dish = session.get(Dish, dish_id)
    if dish is None or dish.status != "on":
        raise NotFound("菜品不存在或已下架")
    liked, like_count = repo.toggle_like(session, student_id=student_id, dish_id=dish_id)
    return DishLikeOut(id=dish_id, like_count=like_count, liked=liked)
