"""统一响应信封与分页壳。

信封：``{code, message, data}``
分页：学生端 ``{records}``；运营端 ``{records, total}``
（见 docs/api/README.md「响应信封」「分页结构」）
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel

SUCCESS_CODE = 200
SUCCESS_MESSAGE = "操作成功"


class Envelope[T](BaseModel):
    code: int = SUCCESS_CODE
    message: str = SUCCESS_MESSAGE
    data: T | None = None


class PageResult[T](BaseModel):
    """学生端分页壳：只有 records（结束判据 = 本页条数 < pageSize）。"""

    records: list[T]


class AdminPageResult[T](BaseModel):
    """运营端分页壳：多一个 total（用于「共 N 条」与总页数）。"""

    records: list[T]
    total: int


def ok(data: Any = None, message: str = SUCCESS_MESSAGE) -> Envelope[Any]:
    return Envelope[Any](code=SUCCESS_CODE, message=message, data=data)


def page_ok(records: list[Any], total: int | None = None) -> Envelope[Any]:
    """按需返回学生端或运营端分页壳。"""
    if total is None:
        payload: Any = PageResult(records=records)
    else:
        payload = AdminPageResult(records=records, total=total)
    return Envelope[Any](code=SUCCESS_CODE, message=SUCCESS_MESSAGE, data=payload)
