"""canteen 域业务规则（docs/func/client/A3-食堂筛选.md）。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Canteen


def list_all(session: Session) -> list[Canteen]:
    """全量食堂，按 id 升序（不筛选、不分页）。"""
    stmt = select(Canteen).order_by(Canteen.id)
    return list(session.scalars(stmt))
