"""issue 域业务规则（docs/func/client/B1-菜品纠错.md、docs/schema/dish_issue.md）。

关键口径：
* 一条提交 = 一个选项（无文字、无图片）
* 去重：同一用户 + 同一菜品 + 同一选项，**未处理**记录至多一条（幂等复用）
* 提交后**不给用户任何回执**
"""

from __future__ import annotations

from sqlalchemy.orm import Session

from app.core.errors import BadRequest, NotFound
from app.models import Dish, DishIssue, DishIssueOption
from app.student.issue import repo


def list_options(session: Session) -> list[DishIssueOption]:
    """全部选项，按 id 升序（无启停、无排序位）。"""
    return repo.list_options(session)


def submit(session: Session, *, student_id: int, dish_id: int, option_id: int) -> None:
    dish = session.get(Dish, dish_id)
    if dish is None or dish.status != "on":
        raise NotFound("菜品不存在或已下架")

    option = session.get(DishIssueOption, option_id)
    if option is None:
        raise BadRequest("纠错选项不存在")

    existing = repo.find_unhandled(
        session, student_id=student_id, dish_id=dish_id, option_id=option_id
    )
    if existing is not None:
        return  # 幂等：不重复提交

    session.add(DishIssue(dish_id=dish_id, user_id=student_id, option_id=option_id))
    session.flush()
