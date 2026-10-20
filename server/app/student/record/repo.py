"""record 域数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Canteen, Dish, DishRating, DishRecord, Stall


def get_on_sale_dish(session: Session, *, dish_id: int) -> Dish | None:
    """在售菜品；不存在或已下架都返回 None（对公开侧等价）。"""
    return session.scalar(select(Dish).where(Dish.id == dish_id, Dish.status == "on"))


def get_dish(session: Session, *, dish_id: int) -> Dish | None:
    return session.get(Dish, dish_id)


def find_unrated(session: Session, *, student_id: int, dish_id: int) -> DishRecord | None:
    """本人该菜的未评分记录（用于幂等复用）。"""
    stmt = (
        select(DishRecord)
        .where(
            DishRecord.user_id == student_id,
            DishRecord.dish_id == dish_id,
            DishRecord.reaction.is_(None),
        )
        .order_by(DishRecord.id.desc())
        .limit(1)
    )
    return session.scalar(stmt)


def get_owned(session: Session, *, student_id: int, record_id: int) -> DishRecord | None:
    """按 id 取记录；**非本人返回 None**（与不存在同响应，防 ID 枚举）。"""
    return session.scalar(
        select(DishRecord).where(DishRecord.id == record_id, DishRecord.user_id == student_id)
    )


def rating_of(session: Session, *, student_id: int, dish_id: int) -> DishRating | None:
    """本人该菜的当前评分（口碑真源 ``dish_rating``，一人一菜一行）。"""
    return session.scalar(
        select(DishRating).where(DishRating.user_id == student_id, DishRating.dish_id == dish_id)
    )


def ratings_of(session: Session, *, student_id: int) -> dict[int, int]:
    """本人**全部**当前评分：``{dish_id: reaction}``（批量一次查询，避免列表 N+1）。"""
    rows = session.execute(
        select(DishRating.dish_id, DishRating.reaction).where(DishRating.user_id == student_id)
    ).all()
    return {dish_id: reaction for dish_id, reaction in rows}


def place_of(session: Session, *, stall_id: int) -> tuple[str, str]:
    """菜品所属**档口名 + 食堂名**（档口缺失时回退空串，不阻断出参）。"""
    row = session.execute(
        select(Stall.name, Canteen.name)
        .outerjoin(Canteen, Canteen.id == Stall.canteen_id)
        .where(Stall.id == stall_id)
    ).first()
    if row is None:
        return "", ""
    return (row[0] or "", row[1] or "")


def list_mine_with_dish(
    session: Session, *, student_id: int
) -> list[tuple[DishRecord, Dish, str, str]]:
    """记录 + 菜品 + 档口名 + 食堂名（一次查询，避免 N+1）。"""
    stmt = (
        select(DishRecord, Dish, Stall.name, Canteen.name)
        .join(Dish, Dish.id == DishRecord.dish_id)
        .outerjoin(Stall, Stall.id == Dish.stall_id)
        .outerjoin(Canteen, Canteen.id == Stall.canteen_id)
        .where(DishRecord.user_id == student_id)
        .order_by(DishRecord.wanted_at.desc())
    )
    return list(session.execute(stmt))
