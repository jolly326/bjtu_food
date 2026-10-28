"""统一时间格式化（出参字符串口径：``YYYY-MM-DD HH:MM:SS``）。

出参时间字段统一由本模块产出，避免各 repo 各自 ``strftime`` 造成口径漂移。
"""

from __future__ import annotations

from datetime import datetime

_DT_FORMAT = "%Y-%m-%d %H:%M:%S"


def format_dt(value: datetime | None) -> str | None:
    """datetime -> 既有出参字符串；空值返回 ``None``。"""
    return value.strftime(_DT_FORMAT) if value else None
