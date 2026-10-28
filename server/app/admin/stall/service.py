"""运营端档口业务规则（docs/func/web/A2-档口管理.md、docs/schema/stall.md）。

* **同一食堂下** ``name`` 唯一（应用层保证，无 DB 唯一索引）
* ``floor`` 是**两端常量**受控字典（值即汉字），服务端按同一清单校验
* 🔴 有下属菜品时**拒绝删除**
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.stall import repo
from app.admin.stall.repo import StallRow
from app.core.cache import invalidate
from app.core.errors import BadRequest, NotFound
from app.models import Canteen, Dish, Stall

# 与前端常量、库内取值逐字一致（docs/schema/stall.md）
FLOOR_VALUES = frozenset({"负一层", "一层", "二层", "三层", "四层"})


def _validate(payload) -> None:
    if not (payload.name or "").strip():
        raise BadRequest("档口名称不能为空")
    if payload.floor and payload.floor not in FLOOR_VALUES:
        raise BadRequest("楼层取值不合法")


def _ensure_name_unique(
    session: Session, *, canteen_id: int, name: str, exclude_stall_id: int | None = None
) -> None:
    """同一食堂下档口名唯一（应用层保证，无 DB 唯一索引）。"""
    if repo.has_same_name(
        session, canteen_id=canteen_id, name=name.strip(), exclude_id=exclude_stall_id
    ):
        raise BadRequest("同一食堂下档口名称已存在")


def list_page(
    session: Session, *, canteen_id: int | None, page: int, size: int
) -> tuple[list[StallRow], int]:
    return repo.list_page(session, canteen_id=canteen_id, page=page, size=size)


def create(session: Session, *, payload) -> StallRow:
    _validate(payload)
    if session.get(Canteen, payload.canteen_id) is None:
        raise NotFound("食堂不存在")
    _ensure_name_unique(session, canteen_id=payload.canteen_id, name=payload.name)

    stall = Stall(
        canteen_id=payload.canteen_id,
        name=payload.name.strip(),
        floor=payload.floor,
        window_no=payload.window_no,
    )
    session.add(stall)
    session.flush()
    invalidate("student:dishes")
    return _reload(session, stall.id)


def update(session: Session, *, stall_id: int, payload) -> StallRow:
    _validate(payload)
    stall = session.get(Stall, stall_id)
    if stall is None:
        raise NotFound("档口不存在")
    if session.get(Canteen, payload.canteen_id) is None:
        raise NotFound("食堂不存在")
    _ensure_name_unique(
        session, canteen_id=payload.canteen_id, name=payload.name, exclude_stall_id=stall_id
    )

    stall.canteen_id = payload.canteen_id
    stall.name = payload.name.strip()
    stall.floor = payload.floor
    stall.window_no = payload.window_no
    session.flush()
    invalidate("student:dishes")
    return _reload(session, stall.id)


def delete(session: Session, *, stall_id: int) -> None:
    stall = session.get(Stall, stall_id)
    if stall is None:
        raise NotFound("档口不存在")

    dish_count = session.scalar(
        select(func.count()).select_from(Dish).where(Dish.stall_id == stall.id)
    )
    if dish_count:
        raise BadRequest("该档口下仍有菜品，请先清空菜品")

    session.delete(stall)
    session.flush()
    invalidate("student:dishes")


def _reload(session: Session, stall_id: int) -> StallRow:
    row = repo.find_row(session, stall_id=stall_id)
    if row is None:
        raise NotFound("档口不存在")
    return row
