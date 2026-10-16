"""运营端食堂数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Canteen


def find_by_name(session: Session, *, name: str) -> Canteen | None:
    return session.scalar(select(Canteen).where(Canteen.name == name))
