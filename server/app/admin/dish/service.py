"""运营端菜品业务规则（docs/func/web/A3-菜品管理.md、docs/schema/dish.md）。

* **同一食堂下** ``name`` 唯一（替换失效的「同一档口下唯一」）
* 归属：**食堂必填**（最小定位），档口 / 楼层**可选**；指定了档口则该档口所属食堂必须与菜品食堂一致
* 🔴 ``like_count`` 只读，**本域不写**
* 🔴 删除**引用即禁删**：存在学生记忆或纠错 ⇒ `400` 引导改用「下架」（CASCADE 仅为兜底，正常路径不可达）
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.dish import repo
from app.admin.dish.repo import DishRow
from app.core.cache import invalidate
from app.core.errors import BadRequest, NotFound
from app.core.storage import to_relative
from app.models import Canteen, Dish, DishIssue, Stall

# 楼层受控字典（与 stall.floor 同源）
FLOOR_CHOICES = ("负一层", "一层", "二层", "三层", "四层")


def _validate(payload) -> None:
    if not (payload.name or "").strip():
        raise BadRequest("菜品名称不能为空")
    if not isinstance(payload.activate, bool):
        raise BadRequest("activate 必须为布尔值")
    if payload.floor and payload.floor not in FLOOR_CHOICES:
        raise BadRequest("楼层取值非法")


def _load_canteen(session: Session, canteen_id: int | None) -> Canteen:
    """食堂必填：最小定位信息，学生端据此能找到地方。"""
    if canteen_id is None:
        raise BadRequest("请选择所属食堂")
    canteen = session.get(Canteen, canteen_id)
    if canteen is None:
        raise NotFound("食堂不存在")
    return canteen


def _load_stall(session: Session, stall_id: int | None, *, canteen_id: int) -> Stall | None:
    """档口可选：未指定返回 None；指定了就必须存在且归属一致。"""
    if stall_id is None:
        return None
    stall = session.get(Stall, stall_id)
    if stall is None:
        raise NotFound("档口不存在")
    if stall.canteen_id is not None and stall.canteen_id != canteen_id:
        raise BadRequest("档口所属食堂与菜品所属食堂不一致")
    return stall


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
    return repo.list_page(
        session,
        stall_id=stall_id,
        canteen_id=canteen_id,
        no_stall=no_stall,
        activate=activate,
        name=name,
        page=page,
        size=size,
    )


def create(session: Session, *, payload) -> DishRow:
    _validate(payload)
    canteen = _load_canteen(session, payload.canteen_id)
    _load_stall(session, payload.stall_id, canteen_id=canteen.id)

    # 同一食堂下 name 唯一
    clash = repo.find_by_canteen_name(session, canteen_id=canteen.id, name=payload.name.strip())
    if clash is not None:
        raise BadRequest("同食堂下菜品名称已存在")

    dish = Dish(
        canteen_id=canteen.id,
        stall_id=payload.stall_id,
        floor=payload.floor,
        name=payload.name.strip(),
        image=to_relative(payload.image_url),
        activate=payload.activate,
    )
    session.add(dish)
    session.flush()
    invalidate("student:dishes")
    return _reload(session, dish.id)


def update(session: Session, *, dish_id: int, payload) -> DishRow:
    _validate(payload)
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")
    canteen = _load_canteen(session, payload.canteen_id)
    _load_stall(session, payload.stall_id, canteen_id=canteen.id)

    # 同一食堂下 name 唯一（不包含自身）
    clash = repo.find_by_canteen_name(
        session, canteen_id=canteen.id, name=payload.name.strip(), exclude_id=dish_id
    )
    if clash is not None:
        raise BadRequest("同食堂下菜品名称已存在")

    dish.canteen_id = canteen.id
    dish.stall_id = payload.stall_id
    dish.floor = payload.floor
    dish.name = payload.name.strip()
    dish.image = to_relative(payload.image_url)
    dish.activate = payload.activate
    session.flush()
    invalidate("student:dishes")
    return _reload(session, dish.id)


def delete(session: Session, *, dish_id: int) -> None:
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")
    # 🔴 引用即禁删：学生纠错一旦随 CASCADE 蒸发不可恢复 —— 请改用「下架」
    issue_count = session.scalar(
        select(func.count()).select_from(DishIssue).where(DishIssue.dish_id == dish_id)
    )
    if issue_count:
        raise BadRequest("该菜品已有学生纠错记录，无法删除，请改用「下架」")
    session.delete(dish)
    session.flush()
    invalidate("student:dishes")


def _reload(session: Session, dish_id: int) -> DishRow:
    row = repo.find_row(session, dish_id=dish_id)
    if row is None:
        raise NotFound("菜品不存在")
    return row
