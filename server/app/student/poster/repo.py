"""poster 域数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Stall


def list_with_poster(session: Session) -> list[Stall]:
    """已上传海报的档口，按 id 升序（顺序由 service 层打乱）。"""
    stmt = select(Stall).where(Stall.poster.is_not(None)).order_by(Stall.id)
    return list(session.scalars(stmt))
