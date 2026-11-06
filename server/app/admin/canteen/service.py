"""运营端食堂业务规则（docs/func/web/A-主数据维护/A1-食堂管理.md）。

* ``name`` 全局唯一（应用层保证，无 DB 唯一索引）
* 🔴 有下属档口时**拒绝删除**
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.canteen import repo
from app.core.errors import BadRequest, NotFound
from app.core.formatters import format_dt
from app.core.runtime.cache import invalidate
from app.core.storage import to_absolute, to_relative
from app.models import Canteen, DishLocation, Stall


@dataclass(slots=True)
class CanteenRow:
    id: int
    name: str
    activate: bool
    image: str | None
    stall_count: int
    updated_at: str


def _to_row(canteen: Canteen, stall_count: int) -> CanteenRow:
    return CanteenRow(
        id=canteen.id,
        name=canteen.name,
        activate=canteen.activate,
        image=to_absolute(canteen.image),
        stall_count=stall_count,
        updated_at=format_dt(canteen.updated_at) or "",
    )


def list_page(session: Session, *, page: int, size: int) -> tuple[list[CanteenRow], int]:
    total = session.scalar(select(func.count()).select_from(Canteen)) or 0
    stmt = select(Canteen).order_by(Canteen.id).offset((page - 1) * size).limit(size)
    canteens = list(session.scalars(stmt))
    ids = [c.id for c in canteens]
    counts: dict[int, int] = {}
    if ids:
        counts = dict(
            session.execute(
                select(Stall.canteen_id, func.count())
                .where(Stall.canteen_id.in_(ids))
                .group_by(Stall.canteen_id)
            ).all()
        )
    return [_to_row(c, int(counts.get(c.id, 0))) for c in canteens], int(total)


def create(
    session: Session, *, name: str, activate: bool = True, image: str | None = None
) -> CanteenRow:
    name = (name or "").strip()
    if not name:
        raise BadRequest("食堂名称不能为空")
    if repo.find_by_name(session, name=name) is not None:
        raise BadRequest("同名食堂已存在")

    canteen = Canteen(name=name, activate=activate, image=to_relative(image))
    session.add(canteen)
    # 事务边界在 service：先落库再失效缓存，避免并发读用旧数据重建缓存
    session.commit()
    invalidate("student:dishes")
    return _to_row(canteen, 0)


def update(
    session: Session,
    *,
    canteen_id: int,
    name: str,
    activate: bool | None = None,
    image: str | None = None,
) -> CanteenRow:
    name = (name or "").strip()
    if not name:
        raise BadRequest("食堂名称不能为空")

    canteen = session.get(Canteen, canteen_id)
    if canteen is None:
        raise NotFound("食堂不存在")

    clash = repo.find_by_name(session, name=name)
    if clash is not None and clash.id != canteen.id:
        raise BadRequest("同名食堂已存在")

    canteen.name = name
    canteen.image = to_relative(image)

    if activate is not None:
        if not activate:
            # RESTRICT：停用食堂前，子级须无启用中的档口
            active_stall_count = session.scalar(
                select(func.count()).select_from(Stall).where(
                    Stall.canteen_id == canteen.id, Stall.activate.is_(True)
                )
            )
            if active_stall_count:
                raise BadRequest("该食堂下仍有启用中的档口，请先停用档口")
        canteen.activate = activate

    session.commit()
    invalidate("student:dishes")
    stall_count = session.scalar(
        select(func.count()).select_from(Stall).where(Stall.canteen_id == canteen.id)
    )
    return _to_row(canteen, int(stall_count or 0))


def delete(session: Session, *, canteen_id: int) -> None:
    canteen = session.get(Canteen, canteen_id)
    if canteen is None:
        raise NotFound("食堂不存在")

    stall_count = session.scalar(
        select(func.count()).select_from(Stall).where(Stall.canteen_id == canteen.id)
    )
    if stall_count:
        raise BadRequest("该食堂下仍有档口，请先清空档口")

    offering_count = session.scalar(
        select(func.count()).select_from(DishLocation).where(DishLocation.canteen_id == canteen.id)
    )
    if offering_count:
        raise BadRequest("该食堂下仍有位置，请先清空位置")

    session.delete(canteen)
    session.commit()
    invalidate("student:dishes")
