"""运营端账号（docs/schema/admin.md）——口令只存 BCrypt 哈希；不被业务表引用。"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Admin(Base):
    __tablename__ = "admin"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    username: Mapped[str] = mapped_column(String(32), nullable=False, unique=True)
    password: Mapped[str] = mapped_column(String(100), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
