"""运营端菜品业务规则（docs/func/web/A3-菜品管理.md、docs/schema/dish.md）。

* **同一档口下** ``name`` 唯一（应用层保证）
* ``price`` 一律以**分**存取
* 🔴 ``avg_reaction`` / ``reaction_count`` 只读，**本域不写**
* 🔴 删除**引用即禁删**：存在学生记忆或纠错 ⇒ `400` 引导改用「下架」（CASCADE 仅为兜底，正常路径不可达）
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.dish import repo
from app.admin.dish.repo import DishRow
from app.core.errors import BadRequest, NotFound
from app.core.storage import to_relative
from app.models import Dish, DishIssue, DishRecord, Stall

VALID_STATUS = frozenset({"on", "off"})


def _validate(payload) -> None:
    if not (payload.name or "").strip():
        raise BadRequest("菜品名称不能为空")
    if payload.price is None or payload.price < 0:
        raise BadRequest("价格不合法（单位：分）")
    if payload.status not in VALID_STATUS:
        raise BadRequest("状态只能取 on（在售）/ off（已下架）")


def list_page(
    session: Session,
    *,
    stall_id: int | None,
    canteen_id: int | None,
    status: str | None,
    page: int,
    size: int,
) -> tuple[list[DishRow], int]:
    return repo.list_page(
        session, stall_id=stall_id, canteen_id=canteen_id, status=status, page=page, size=size
    )


def create(session: Session, *, payload) -> DishRow:
    _validate(payload)
    if session.get(Stall, payload.stall_id) is None:
        raise NotFound("档口不存在")

    # 同一档口下 name 唯一（应用层保证，暂无 DB 唯一索引）
    clash = repo.find_by_stall_name(session, stall_id=payload.stall_id, name=payload.name.strip())
    if clash is not None:
        raise BadRequest("同档口下菜品名称已存在")

    dish = Dish(
        stall_id=payload.stall_id,
        name=payload.name.strip(),
        price=payload.price,
        image=to_relative(payload.image_url),
        status=payload.status,
    )
    session.add(dish)
    session.flush()
    return _reload(session, dish.id)


def update(session: Session, *, dish_id: int, payload) -> DishRow:
    _validate(payload)
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")
    if session.get(Stall, payload.stall_id) is None:
        raise NotFound("档口不存在")

    # 同一档口下 name 唯一（不包含自身）
    clash = repo.find_by_stall_name(
        session, stall_id=payload.stall_id, name=payload.name.strip(), exclude_id=dish_id
    )
    if clash is not None:
        raise BadRequest("同档口下菜品名称已存在")

    dish.stall_id = payload.stall_id
    dish.name = payload.name.strip()
    dish.price = payload.price
    dish.image = to_relative(payload.image_url)
    dish.status = payload.status
    session.flush()
    return _reload(session, dish.id)


def delete(session: Session, *, dish_id: int) -> None:
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")
    # 🔴 引用即禁删：学生记忆 / 纠错一旦随 CASCADE 蒸发不可恢复 —— 请改用「下架」
    record_count = session.scalar(
        select(func.count()).select_from(DishRecord).where(DishRecord.dish_id == dish_id)
    )
    issue_count = session.scalar(
        select(func.count()).select_from(DishIssue).where(DishIssue.dish_id == dish_id)
    )
    if record_count or issue_count:
        raise BadRequest("该菜品已有学生记忆或纠错记录，无法删除，请改用「下架」")
    session.delete(dish)
    session.flush()


def _reload(session: Session, dish_id: int) -> DishRow:
    row = repo.find_row(session, dish_id=dish_id)
    if row is None:
        raise NotFound("菜品不存在")
    return row
