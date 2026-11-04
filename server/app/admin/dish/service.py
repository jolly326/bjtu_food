"""运营端菜品业务规则（docs/func/web/A-主数据维护/A3-菜品管理.md、docs/schema/dish.md）。

* ``name`` **全库唯一**（应用层判重，DB 另有唯一键兜底）
* 售卖点随菜品整表替换：带 ``id`` 更新、不带 ``id`` 新增、未出现的删除
* ``stallId`` 非空时其档口 ``canteen_id`` 必须等于售卖点 ``canteenId``（否则 ``400``）
* ``description`` 变更时重算 ``embedding``
"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.admin.dish import repo
from app.admin.dish.repo import DishRow
from app.core.runtime.cache import invalidate
from app.core.search.embedding import current_model, embed
from app.core.errors import BadRequest, NotFound
from app.core.storage import to_relative
from app.models import Canteen, Dish, DishOffering, Stall


def _validate(payload) -> None:
    if not (payload.name or "").strip():
        raise BadRequest("菜品名称不能为空")


def _check_offering(session: Session, item) -> None:
    canteen = session.get(Canteen, item.canteen_id)
    if canteen is None:
        raise NotFound("食堂不存在")
    if item.stall_id is not None:
        stall = session.get(Stall, item.stall_id)
        if stall is None:
            raise NotFound("档口不存在")
        if stall.canteen_id != canteen.id:
            raise BadRequest("档口所属食堂与售卖点食堂不一致")


def _set_embedding(dish: Dish) -> None:
    """保存时按当前生效模型重算向量，并记录模型标识（检索端据此判断能否复用）。"""
    text = (dish.description or dish.name or "").strip()
    dish.embedding = embed(text)
    dish.embedding_model = current_model()


def _replace_offerings(session: Session, dish: Dish, items: list) -> None:
    existing = {
        off.id: off
        for off in session.scalars(
            select(DishOffering).where(DishOffering.dish_id == dish.id)
        )
    }
    kept: set[int] = set()
    for item in items:
        _check_offering(session, item)
        item_id = getattr(item, "id", None)
        if item_id is not None and item_id in existing:
            off = existing[item_id]
            kept.add(off.id)
            off.canteen_id = item.canteen_id
            off.stall_id = item.stall_id
            off.price = item.price
            off.activate = item.activate
        else:
            session.add(
                DishOffering(
                    dish_id=dish.id,
                    canteen_id=item.canteen_id,
                    stall_id=item.stall_id,
                    price=item.price,
                    activate=item.activate,
                )
            )
    for offering_id, offering in existing.items():
        if offering_id not in kept:
            session.delete(offering)


def create(session: Session, *, payload) -> DishRow:
    _validate(payload)
    name = payload.name.strip()
    if repo.find_by_name(session, name=name) is not None:
        raise BadRequest("菜品名称已存在")

    dish = Dish(
        name=name,
        description=payload.description or None,
        image=to_relative(payload.image_url),
    )
    _set_embedding(dish)
    session.add(dish)
    session.flush()

    _replace_offerings(session, dish, payload.offerings or [])
    session.flush()
    invalidate("student:dishes")
    return _reload(session, dish.id)


def update(session: Session, *, dish_id: int, payload) -> DishRow:
    _validate(payload)
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")

    name = payload.name.strip()
    if repo.find_by_name(session, name=name, exclude_id=dish_id) is not None:
        raise BadRequest("菜品名称已存在")

    dish.name = name
    dish.description = payload.description or None
    dish.image = to_relative(payload.image_url)
    _set_embedding(dish)

    _replace_offerings(session, dish, payload.offerings or [])
    session.flush()
    invalidate("student:dishes")
    return _reload(session, dish.id)


def delete(session: Session, *, dish_id: int) -> None:
    dish = session.get(Dish, dish_id)
    if dish is None:
        raise NotFound("菜品不存在")
    # dish_offering / feedback 随外键 ON DELETE CASCADE 一并清除（删除前由前端提醒）
    session.delete(dish)
    session.flush()
    invalidate("student:dishes")


def _reload(session: Session, dish_id: int) -> DishRow:
    row = repo.find_row(session, dish_id=dish_id)
    if row is None:
        raise NotFound("菜品不存在")
    return row
