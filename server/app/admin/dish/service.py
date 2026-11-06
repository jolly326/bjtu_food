"""运营端菜品业务规则（docs/func/web/A-主数据维护/A3-菜品管理.md、docs/schema/dish.md）。

* 一道菜 = 本体（名称 / 描述 / 图 / 价格）+ N 个位置（食堂 / 档口 / 在售）
* 位置随菜品整表替换：带 ``id`` 更新、不带 ``id`` 新增、未出现的删除
* 档口非空时其 ``canteen_id`` 必须等于位置食堂（否则 ``400``）
* ``description`` 变更时重算当前模型向量（写入 ``dish_embedding``）
"""

from __future__ import annotations

from typing import Any

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


def list_page(
    session: Session,
    *,
    canteen_id: int | None,
    stall_id: int | None,
    activate: bool | None,
    name: str | None,
    page: int,
    size: int,
) -> tuple[list[DishRow], int]:
    return repo.list_page(
        session,
        canteen_id=canteen_id,
        stall_id=stall_id,
        activate=activate,
        name=name,
        page=page,
        size=size,
    )


def _replace_locations(session: Session, dish: Dish, items: list) -> None:
    """位置整表替换：先清空既有行，再按入参重建（保留传入 ``id``）。

    重建而不是「原地改 + 追加」是为了避开唯一键 ``uk_location_dish_canteen_stall``：
    同一 (食堂, 档口) 在本轮先删后建、或两条位置互换食堂 / 档口时，
    「先增后删」会在 ``flush`` 时撞唯一索引。
    """
    existing_ids = set(
        session.scalars(select(DishLocation.id).where(DishLocation.dish_id == dish.id))
    )
    resolved: list[tuple[int | None, Any]] = []
    seen: set[tuple[int, int | None]] = set()
    for item in items:
        _check_location(session, item)
        key = (item.canteen_id, item.stall_id)
        if key in seen:
            raise BadRequest("同一食堂 / 档口的位置重复")
        seen.add(key)
        item_id = getattr(item, "id", None)
        if item_id is not None and item_id not in existing_ids:
            raise BadRequest("位置 id 不属于该菜品")
        resolved.append((item_id, item))

    for location in session.scalars(
        select(DishLocation).where(DishLocation.dish_id == dish.id)
    ):
        session.delete(location)
    session.flush()

    # 先落带 id 的行（保留既有位置标识），再落新增行，避免自增 id 抢占待复用 id
    for item_id, item in sorted(resolved, key=lambda pair: pair[0] is None):
        session.add(
            DishLocation(
                id=item_id,
                dish_id=dish.id,
                canteen_id=item.canteen_id,
                stall_id=item.stall_id,
                activate=item.activate,
            )
        )


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
    # 事务边界在 service：先落库再失效缓存，避免并发读用旧数据重建缓存
    session.commit()
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
    # 事务边界在 service：先落库再失效缓存，避免并发读用旧数据重建缓存
    session.commit()
    invalidate("student:dishes")
    return _reload(session, dish.id)


def delete(session: Session, *, dish_id: int) -> None:
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")
    session.delete(dish)
    session.commit()
    invalidate("student:dishes")


def _reload(session: Session, dish_id: int) -> DishRow:
    row = repo.find_row(session, dish_id=dish_id)
    if row is None:
        raise NotFound("菜品不存在")
    return row
