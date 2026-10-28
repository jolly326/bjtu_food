"""dish 域数据访问。

美食地图全量在售菜品 + 个人点赞态；点赞 toggle 与点赞数重算（docs/schema/dish.md、dish_rating.md）。
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import Canteen, Dish, DishRating, Stall


@dataclass(slots=True)
class DishRow:
    """在售菜品 + 位置链路（食堂 / 档口）+ 群体点赞数。"""

    id: int
    name: str
    image: str | None
    like_count: int
    canteen_name: str
    stall_name: str
    floor: str | None


def list_on_sale(session: Session) -> list[DishRow]:
    """全量在售菜品 + 位置 + 点赞数；不做食堂筛选 / 分页（错位密铺由端上完成）。"""
    stmt = (
        select(Dish, Stall.name, Canteen.name, Stall.floor)
        .join(Stall, Stall.id == Dish.stall_id)
        .join(Canteen, Canteen.id == Stall.canteen_id)
        .where(Dish.status == "on")
    )
    rows: list[DishRow] = []
    for dish, stall_name, canteen_name, floor in session.execute(stmt):
        rows.append(
            DishRow(
                id=dish.id,
                name=dish.name,
                image=dish.image,
                like_count=dish.like_count,
                canteen_name=canteen_name,
                stall_name=stall_name,
                floor=floor,
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

    并发下首次插入用 SAVEPOINT 隔离：命中唯一键冲突则回退读取既有行再取反，
    避免并发请求抛 ``IntegrityError`` 变 500。返回 ``(liked, like_count)``。
    """
    rating = session.scalar(
        select(DishRating).where(
            DishRating.user_id == student_id, DishRating.dish_id == dish_id
        )
    )
    if rating is None:
        try:
            with session.begin_nested():
                session.add(DishRating(user_id=student_id, dish_id=dish_id, liked=True))
            liked = True
        except IntegrityError:
            # 并发插入被唯一键拦下：回退读既有行并取反
            rating = session.scalar(
                select(DishRating).where(
                    DishRating.user_id == student_id, DishRating.dish_id == dish_id
                )
            )
            if rating is None:
                raise
            rating.liked = not rating.liked
            rating.reacted_at = func.now()
            session.flush()
            liked = bool(rating.liked)
    else:
        rating.liked = not rating.liked
        rating.reacted_at = func.now()
        session.flush()
        liked = bool(rating.liked)

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
