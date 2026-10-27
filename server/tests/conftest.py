"""测试公共配置。

SQLite 只对 ``INTEGER PRIMARY KEY`` 自增，而本仓主键为 ``BIGINT``（对齐 MySQL 的
``BIGINT AUTO_INCREMENT``）。在 sqlite 方言下把 ``BIGINT`` 编译成 ``INTEGER``，
让测试库的自增 / 外键行为与生产库一致；MySQL 方言不受影响。
"""

from __future__ import annotations

from sqlalchemy import BigInteger
from sqlalchemy.ext.compiler import compiles


@compiles(BigInteger, "sqlite")
def _bigint_as_integer(_element: BigInteger, _compiler: object, **_kw: object) -> str:
    return "INTEGER"
