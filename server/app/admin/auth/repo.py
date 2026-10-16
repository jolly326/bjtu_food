"""运营端登录数据访问。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Admin


def find_by_username(session: Session, *, username: str) -> Admin | None:
    return session.scalar(select(Admin).where(Admin.username == username))
