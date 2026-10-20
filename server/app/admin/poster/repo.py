"""运营端海报数据访问。"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.storage import to_absolute
from app.models import Canteen, Poster, Stall


@dataclass(slots=True)
class PosterRow:
    """海报行 + 位置链路（image 为**绝对 URL**；档口可空 ⇒ 名称可能为空串）。"""

    id: int
    stall_id: int | None
    stall_name: str
    canteen_name: str
    image: str
    sort: int
    status: str
    updated_at: str


def _to_row(poster: Poster, stall_name: str, canteen_name: str) -> PosterRow:
    return PosterRow(
        id=poster.id,
        stall_id=poster.stall_id,
        stall_name=stall_name,
        canteen_name=canteen_name,
        image=to_absolute(poster.image) or "",
        sort=poster.sort,
        status=poster.status,
        updated_at=poster.updated_at.strftime("%Y-%m-%d %H:%M:%S") if poster.updated_at else "",
    )


def find_row(session: Session, *, poster_id: int) -> PosterRow | None:
    stmt = (
        select(Poster, Stall.name, Canteen.name)
        .outerjoin(Stall, Stall.id == Poster.stall_id)
        .outerjoin(Canteen, Canteen.id == Stall.canteen_id)
        .where(Poster.id == poster_id)
    )
    found = session.execute(stmt).first()
    if found is None:
        return None
    poster, stall_name, canteen_name = found
    return _to_row(poster, stall_name or "", canteen_name or "")


def list_page(
    session: Session,
    *,
    stall_id: int | None,
    canteen_id: int | None,
    status: str | None,
    page: int,
    size: int,
) -> tuple[list[PosterRow], int]:
    stmt = (
        select(Poster, Stall.name, Canteen.name)
        .outerjoin(Stall, Stall.id == Poster.stall_id)
        .outerjoin(Canteen, Canteen.id == Stall.canteen_id)
        .order_by(Poster.sort, Poster.id)
    )
    if stall_id is not None:
        stmt = stmt.where(Poster.stall_id == stall_id)
    if canteen_id is not None:
        stmt = stmt.where(Stall.canteen_id == canteen_id)
    if status is not None:
        stmt = stmt.where(Poster.status == status)

    total = session.scalar(select(func.count()).select_from(stmt.order_by(None).subquery()))
    pairs = session.execute(stmt.offset((page - 1) * size).limit(size)).all()
    return [_to_row(p, s or "", c or "") for p, s, c in pairs], int(total or 0)