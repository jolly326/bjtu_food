"""运营端纠错选项字典业务规则（docs/func/web/B3-纠错选项管理.md）。

* 字段只有文案；**无启停列、无排序列**
* 改名**全站生效**（文案锚在 id，历史提交的中文随展示翻译）
* 🔴 **被提交记录引用时禁删**
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.option import repo
from app.admin.option.repo import OptionRow
from app.core.errors import BadRequest, NotFound
from app.models import DishIssue, DishIssueOption


def list_all(session: Session) -> list[OptionRow]:
    return repo.list_all(session)


def create(session: Session, *, name: str) -> OptionRow:
    name = (name or "").strip()
    if not name:
        raise BadRequest("选项文案不能为空")

    option = DishIssueOption(name=name)
    session.add(option)
    session.flush()
    return repo.find_row(session, option_id=option.id) or OptionRow(
        id=option.id, name=option.name, ref_count=0, updated_at=""
    )


def update(session: Session, *, option_id: int, name: str) -> OptionRow:
    name = (name or "").strip()
    if not name:
        raise BadRequest("选项文案不能为空")

    option = session.get(DishIssueOption, option_id)
    if option is None:
        raise NotFound("选项不存在")

    option.name = name
    session.flush()
    return repo.find_row(session, option_id=option.id) or OptionRow(
        id=option.id, name=option.name, ref_count=0, updated_at=""
    )


def delete(session: Session, *, option_id: int) -> None:
    option = session.get(DishIssueOption, option_id)
    if option is None:
        raise NotFound("选项不存在")

    ref_count = session.scalar(
        select(func.count()).select_from(DishIssue).where(DishIssue.option_id == option.id)
    )
    if ref_count:
        raise BadRequest(f"该选项已被 {int(ref_count)} 条纠错提交引用，无法删除")

    session.delete(option)
    session.flush()
