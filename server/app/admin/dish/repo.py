"""运营端菜品数据访问。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.formatters import format_dt
from app.core.storage import to_absolute
from app.models import Canteen, Dish, Stall


@dataclass(slots=True)
class DishRow:
    """菜品行 + 位置链路（image 为**绝对 URL**）。

    档口 / 楼层可缺失 ⇒ **用空串表达**（保持非空类型，列表展示占位「暂无」）。
    """

    id: int
    canteen_id: int
    canteen_name: str
    stall_id: int | None
    stall_name: str
    floor: str
    name: str
    image: str | None
    activate: bool
    like_count: int
    updated_at: str


def _to_row(dish: Dish, stall_name: str, canteen_name: str, floor: str) -> DishRow:
    return DishRow(
        id=dish.id,
        canteen_id=dish.canteen_id,
        canteen_name=canteen_name,
        stall_id=dish.stall_id,
        stall_name=stall_name,
        floor=floor,
        name=dish.name,
        image=to_absolute(dish.image),
        activate=dish.activate,
        like_count=dish.like_count,
        updated_at=format_dt(dish.updated_at) or "",
    )


def _select_row():
    """位置链路：食堂必填走 INNER JOIN，**档口可空走 LEFT JOIN**；
    楼层优先取菜品自身 ``floor``，缺失时回落档口楼层。"""
    return (
        select(
            Dish,
            func.coalesce(Stall.name, ""),
            Canteen.name,
            func.coalesce(Dish.floor, Stall.floor, ""),
        )
        .join(Canteen, Canteen.id == Dish.canteen_id)
        .outerjoin(Stall, Stall.id == Dish.stall_id)
    )


def find_row(session: Session, *, dish_id: int) -> DishRow | None:
    found = session.execute(_select_row().where(Dish.id == dish_id)).first()
    if found is None:
        return None
    dish, stall_name, canteen_name, floor = found
    return _to_row(dish, stall_name, canteen_name, floor)


def list_page(
    session: Session,
    *,
    stall_id: int | None,
    canteen_id: int | None,
    no_stall: bool | None,
    activate: bool | None,
    name: str | None,
    page: int,
    size: int,
) -> tuple[list[DishRow], int]:
    stmt = _select_row().order_by(Dish.id)
    if stall_id is not None:
        stmt = stmt.where(Dish.stall_id == stall_id)
    if no_stall:
        stmt = stmt.where(Dish.stall_id.is_(None))
    if canteen_id is not None:
        stmt = stmt.where(Dish.canteen_id == canteen_id)
    if activate is not None:
        stmt = stmt.where(Dish.activate == activate)
    if name:
        stmt = stmt.where(Dish.name.like(f"%{name}%"))

    # 计数：食堂已是 dish 自身字段，无需再 join
    count_stmt = select(func.count(Dish.id)).select_from(Dish)
    if stall_id is not None:
        count_stmt = count_stmt.where(Dish.stall_id == stall_id)
    if no_stall:
        count_stmt = count_stmt.where(Dish.stall_id.is_(None))
    if canteen_id is not None:
        count_stmt = count_stmt.where(Dish.canteen_id == canteen_id)
    if activate is not None:
        count_stmt = count_stmt.where(Dish.activate == activate)
    if name:
        count_stmt = count_stmt.where(Dish.name.like(f"%{name}%"))
    total = session.scalar(count_stmt)

    pairs = session.execute(stmt.offset((page - 1) * size).limit(size)).all()
    return [_to_row(d, s, c, f) for d, s, c, f in pairs], int(total or 0)


def find_by_canteen_name(
    session: Session, *, canteen_id: int, name: str, exclude_id: int | None = None
) -> Dish | None:
    """同一食堂下同名菜品；可排除自身（update 用）"""
    stmt = select(Dish).where(Dish.canteen_id == canteen_id, Dish.name == name)
    if exclude_id is not None:
        stmt = stmt.where(Dish.id != exclude_id)
    return session.scalar(stmt)
