"""分页参数归一化。

口径（docs/api/README.md）：

* 学生端：``pageSize`` 上限 50，超限服务端截断
* 运营端：默认 20，上限 50
"""

from __future__ import annotations

from dataclasses import dataclass

MAX_PAGE_SIZE = 50
DEFAULT_PAGE_SIZE = 20


@dataclass(frozen=True)
class Page:
    page: int
    size: int

    @property
    def offset(self) -> int:
        return (self.page - 1) * self.size


def normalize_page(page: int | None, size: int | None, *, default_size: int = DEFAULT_PAGE_SIZE) -> Page:
    """把请求参数收敛为合法分页（page >= 1，1 <= size <= 50）。"""
    p = page if isinstance(page, int) and page >= 1 else 1
    s = size if isinstance(size, int) and size >= 1 else default_size
    s = min(s, MAX_PAGE_SIZE)
    return Page(page=p, size=s)
