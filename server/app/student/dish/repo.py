"""dish 域数据访问。

美食地图全量在售菜品 + 个人点赞态；点赞 toggle 与点赞数重算（docs/schema/dish.md、dish_rating.md）。
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Canteen, Dish, DishRating, Stall


@dataclass(slots=True)
class DishRow:
    """在售菜品 + 位置链路（食堂 / 档口）+ 群体点赞数。"""

    id: int
    name: str
    price: int
    image: str | None
    like_count: int
    canteen_name: str
    stall_name: str


def list_on_sale(session: Session) -> list[DishRow]:
    """全量在售菜品 + 位置 + 点赞数；不做食堂筛选 / 分页（随机散布由端上完成）。"""
    stmt = (
        select(Dish, Stall.name, Canteen.name)
        .join(Stall, Stall.id == Dish.stall_id)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .where(Dish.status == "on")
    )
    rows: list[DishRow] = []
    for dish, stall_name, canteen_name in session.execute(stmt):
        rows.append(
            DishRow(
                id=dish.id,
                name=dish.name,
                price=dish.price,
                image=dish.image,
                like_count=dish.like_count,
                canteen_name=canteen_name,
                stall_name=stall_name,
            )
        )
    return rows


def liked_dish_ids(session: Session, *, student_id: int) -> set[int]:
    """当前用户已赞（liked = 1）的菜品 id。"""
    stmt = select(DishRating.dish_id).where(
        DishRating.user_id == student_id, DishRating.liked.is_(True)
    )
    return set(session.scalars(stmt))


def toggle_like(session: Session, *, student_id: int, dish_id: int) -> tuple[bool, int]:
    """两态点赞 toggle：已赞 ⇒ 取消，未赞 ⇒ 点赞；同事务重算 ``dish.like_count``。

    返回 ``(liked, like_count)`` —— liked 为操作后状态，like_count 为最新点赞数。
    """
    rating = session.scalar(
        select(DishRating).where(
            DishRating.user_id == student_id, DishRating.dish_id == dish_id
        )
    )
    if rating is None:
        rating = DishRating(user_id=student_id, dish_id=dish_id, liked=True)
        session.add(rating)
        liked = True
    elif rating.liked:
        rating.liked = False
        liked = False
    else:
        rating.liked = True
        liked = True
    rating.reacted_at = func.now()
    session.flush()

    like_count = int(
        session.scalar(
            select(func.count(DishRating.id)).where(
                DishRating.dish_id == dish_id, DishRating.liked.is_(True)
            )
        )
        or 0
    )
    dish = session.get(Dish, dish_id)
    if dish is not None:
        dish.like_count = like_count
    session.flush()
    return liked, like_count
