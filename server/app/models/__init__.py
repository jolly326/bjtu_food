"""ORM 模型层（跨域共享，9 张表，按表拆文件）。

🔴 列 / 类型 / 可空 / 默认 / 索引 / 外键 / 级联规则**全部对应** ``docs/schema/*.md``，
在此处**新增列 / 改类型 / 加唯一键**都视为违反真源，须先改文档。

时间戳纪律：``created_at`` / ``updated_at`` 由 MySQL 写入
（``server_default`` + ``onupdate``），**应用层不赋值**。
"""

from __future__ import annotations

from app.models.admin import Admin
from app.models.base import Base
from app.models.canteen import Canteen
from app.models.dish import Dish
from app.models.dish_issue import DishIssue
from app.models.dish_issue_option import DishIssueOption
from app.models.dish_record import DishRecord
from app.models.poster import Poster
from app.models.stall import Stall
from app.models.user import User

__all__ = [
    "Admin",
    "Base",
    "Canteen",
    "Dish",
    "DishIssue",
    "DishIssueOption",
    "DishRecord",
    "Poster",
    "Stall",
    "User",
]
