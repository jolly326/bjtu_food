"""海报（docs/schema/poster.md）——一档口多张，独立上下架与运营固定序。"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Poster(Base):
    __tablename__ = "poster"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    stall_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("stall.id", ondelete="CASCADE"), nullable=False, index=True
    )
    image: Mapped[str] = mapped_column(String(255), nullable=False)
    sort: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    status: Mapped[str] = mapped_column(
        String(32), nullable=False, server_default="on", index=True
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )