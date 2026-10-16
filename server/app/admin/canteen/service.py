"""运营端食堂业务规则（docs/func/web/A1-食堂管理.md）。

* ``name`` 全局唯一（应用层保证，无 DB 唯一索引）
* 🔴 有下属档口时**拒绝删除**
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.canteen import repo
from app.core.errors import BadRequest, NotFound
from app.models import Canteen, Stall


@dataclass(slots=True)
class CanteenRow:
    id: int
    name: str
    stall_count: int
    updated_at: str


def _to_row(canteen: Canteen, stall_count: int) -> CanteenRow:
    return CanteenRow(
        id=canteen.id,
        name=canteen.name,
        stall_count=stall_count,
        updated_at=canteen.updated_at.strftime("%Y-%m-%d %H:%M:%S") if canteen.updated_at else "",
    )


def list_page(session: Session, *, page: int, size: int) -> tuple[list[CanteenRow], int]:
    total = session.scalar(select(func.count()).select_from(Canteen)) or 0
    stmt = select(Canteen).order_by(Canteen.id).offset((page - 1) * size).limit(size)
    counts = dict(
        session.execute(select(Stall.canteen_id, func.count()).group_by(Stall.canteen_id)).all()
    )
    return [_to_row(c, int(counts.get(c.id, 0))) for c in session.scalars(stmt)], int(total)


def create(session: Session, *, name: str) -> CanteenRow:
    name = (name or "").strip()
    if not name:
        raise BadRequest("食堂名称不能为空")
    if repo.find_by_name(session, name=name) is not None:
        raise BadRequest("同名食堂已存在")

    canteen = Canteen(name=name)
    session.add(canteen)
    session.flush()
    return _to_row(canteen, 0)


def update(session: Session, *, canteen_id: int, name: str) -> CanteenRow:
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
    session.flush()
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

    session.delete(canteen)
    session.flush()
