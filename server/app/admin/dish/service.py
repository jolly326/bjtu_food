"""运营端菜品业务规则（docs/func/web/A-主数据维护/A3-菜品管理.md、docs/schema/dish.md）。

* 一道菜 = 本体（名称 / 描述 / 图 / 价格）+ N 个位置（食堂 / 档口 / 在售）
* 位置随菜品整表替换：带 ``id`` 更新、不带 ``id`` 新增、未出现的删除
* 档口非空时其 ``canteen_id`` 必须等于位置食堂（否则 ``400``）
* ``description`` 变更时重算当前模型向量（写入 ``dish_embedding``）
"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.admin.dish import repo
from app.admin.dish.repo import DishRow
from app.core.errors import BadRequest, NotFound
from app.core.runtime.cache import invalidate
from app.core.search.embedding import current_model, embed
from app.core.storage import to_relative
from app.models import Canteen, Dish, DishEmbedding, DishLocation, Stall


def _validate(payload) -> None:
    if not (payload.name or "").strip():
        raise BadRequest("菜品名称不能为空")


def _check_location(session: Session, item) -> None:
    canteen = session.get(Canteen, item.canteen_id)
    if canteen is None:
        raise NotFound("食堂不存在")
    if item.stall_id is not None:
        stall = session.get(Stall, item.stall_id)
        if stall is None:
            raise NotFound("档口不存在")
        if stall.canteen_id != canteen.id:
            raise BadRequest("档口所属食堂与位置食堂不一致")


def _set_embedding(session: Session, dish: Dish) -> None:
    text = (dish.description or dish.name or "").strip()
    model = current_model()
    row = session.get(DishEmbedding, (dish.id, model))
    if row is None:
        session.add(DishEmbedding(dish_id=dish.id, model=model, vector=embed(text)))
    else:
        row.vector = embed(text)


def _replace_locations(session: Session, dish: Dish, items: list) -> None:
    existing = {
        loc.id: loc
        for loc in session.scalars(
            select(DishLocation).where(DishLocation.dish_id == dish.id)
        )
    }
    kept: set[int] = set()
    seen: set[tuple[int, int | None]] = set()
    for item in items:
        _check_location(session, item)
        key = (item.canteen_id, item.stall_id)
        if key in seen:
            raise BadRequest("同一食堂 / 档口的位置重复")
        seen.add(key)
        item_id = getattr(item, "id", None)
        if item_id is not None and item_id in existing:
            loc = existing[item_id]
            kept.add(loc.id)
            loc.canteen_id = item.canteen_id
            loc.stall_id = item.stall_id
            loc.activate = item.activate
        else:
            session.add(
                DishLocation(
                    dish_id=dish.id,
                    canteen_id=item.canteen_id,
                    stall_id=item.stall_id,
                    activate=item.activate,
                )
            )
    for location_id, location in existing.items():
        if location_id not in kept:
            session.delete(location)


def create(session: Session, *, payload) -> DishRow:
    _validate(payload)
    dish = Dish(
        name=payload.name.strip(),
        description=payload.description or None,
        image=to_relative(payload.image_url),
        price=payload.price,
    )
    session.add(dish)
    session.flush()
    _replace_locations(session, dish, payload.locations or [])
    _set_embedding(session, dish)
    session.flush()
    invalidate("student:dishes")
    return _reload(session, dish.id)


def update(session: Session, *, dish_id: int, payload) -> DishRow:
    _validate(payload)
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")

    dish.name = payload.name.strip()
    dish.description = payload.description or None
    dish.image = to_relative(payload.image_url)
    dish.price = payload.price
    _replace_locations(session, dish, payload.locations or [])
    _set_embedding(session, dish)
    session.flush()
    invalidate("student:dishes")
    return _reload(session, dish.id)


def delete(session: Session, *, dish_id: int) -> None:
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")
    session.delete(dish)
    session.flush()
    invalidate("student:dishes")


def _reload(session: Session, dish_id: int) -> DishRow:
    row = repo.find_row(session, dish_id=dish_id)
    if row is None:
        raise NotFound("菜品不存在")
    return row
