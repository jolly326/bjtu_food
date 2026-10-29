"""档口（docs/schema/stall.md）——菜品位置归属。floor 是两端常量受控字典。"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Stall(Base):
    __tablename__ = "stall"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    canteen_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("canteen.id"), nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(32), nullable=False, server_default="")
    activate: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="1")
    floor: Mapped[str | None] = mapped_column(String(8), nullable=True)
    window_no: Mapped[str | None] = mapped_column(String(16), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )
