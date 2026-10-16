"""canteen 域数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Canteen


def list_all(session: Session) -> list[Canteen]:
    stmt = select(Canteen).order_by(Canteen.id)
    return list(session.scalars(stmt))
