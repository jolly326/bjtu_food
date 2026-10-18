"""poster 域数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Poster


def list_on_shelf(session: Session) -> list[Poster]:
    """上架海报，按运营固定序（sort 升序，同值按创建顺序）。"""
    stmt = select(Poster).where(Poster.status == "on").order_by(Poster.sort, Poster.id)
    return list(session.scalars(stmt))
