"""食堂（docs/schema/canteen.md）——名称唯一性由应用层保证。"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Canteen(Base):
    __tablename__ = "canteen"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(32), nullable=False, server_default="")
    activate: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="1")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
