"""运营端纠错处置业务规则（docs/func/web/B2-纠错查看.md、docs/schema/dish_issue.md）。

* 列表**默认只查未处理**（handled=false）⇒ 规模 = 待办量
* 标记处理写 ``handledAt``；``handled=false`` 清空（撤销误标）
* 🔴 不修改菜品、不给学生回执、不删除提交行
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy.orm import Session

from app.admin.issue import repo
from app.admin.issue.repo import IssueRow
from app.core.errors import NotFound
from app.models import DishIssue


def list_page(
    session: Session,
    *,
    handled: bool | None,
    dish_id: int | None,
    page: int,
    size: int,
) -> tuple[list[IssueRow], int]:
    return repo.list_page(session, handled=handled, dish_id=dish_id, page=page, size=size)


def set_handled(session: Session, *, issue_id: int, handled: bool) -> IssueRow:
    issue = session.get(DishIssue, issue_id)
    if issue is None:
        raise NotFound("纠错记录不存在")

    issue.handled_at = datetime.now() if handled else None
    session.flush()

    row = repo.find_row(session, issue_id=issue_id)
    if row is None:
        raise NotFound("纠错记录不存在")
    return row
