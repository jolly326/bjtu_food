"""record 域数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Dish, DishRecord


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


def list_mine_with_dish(session: Session, *, student_id: int) -> list[tuple[DishRecord, Dish]]:
    stmt = (
        select(DishRecord, Dish)
        .join(Dish, Dish.id == DishRecord.dish_id)
        .where(DishRecord.user_id == student_id)
        .order_by(DishRecord.wanted_at.desc())
    )
    return list(session.execute(stmt))
