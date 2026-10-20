"""海报（docs/schema/poster.md）——运营内容，**档口可选**（不强制绑定），独立上下架与运营固定序。"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Poster(Base):
    __tablename__ = "poster"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    # 🔴 可空：海报是运营内容、不强制绑档口；档口删除 ⇒ SET NULL（海报保留，仅来源标注消失）
    stall_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("stall.id", ondelete="SET NULL"), nullable=True, index=True
    )
    image: Mapped[str] = mapped_column(String(255), nullable=False)
    sort: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    status: Mapped[str] = mapped_column(
        String(32), nullable=False, server_default="on", index=True
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )