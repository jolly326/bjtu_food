"""auth 域数据访问。

本域直接复用 ``User`` 模型的唯一键查询，无需额外仓储逻辑；
保留该模块以满足分层结构（router -> service -> repo）。
"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import User


def find_id_by_openid(session: Session, openid: str) -> int | None:
    return session.scalar(select(User.id).where(User.openid == openid))
