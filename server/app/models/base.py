"""ORM 声明式基类（9 张表共用）。"""

from __future__ import annotations

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """声明式基类。"""
