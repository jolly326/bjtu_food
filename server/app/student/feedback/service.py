"""feedback 域业务规则（docs/func/client/B-轻量反馈/B1-文本反馈.md、docs/schema/feedback.md）。

* 两类：``product`` 产品反馈 / ``dish`` 菜品反馈（``dish`` 必须带有效菜品）
* 正文 1–500 字纯文本，提交前走微信内容安全（``msgSecCheck``）
* 不给回执、不提供查询
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.content_security import check_text
from app.core.errors import BadRequest, NotFound
from app.models import Dish, DishLocation
from app.student.feedback import repo

_TYPES = {"product", "dish"}


def submit(
    session: Session,
    *,
    student_id: int,
    type_: str,
    dish_id: int | None,
    content: str,
) -> None:
    kind = (type_ or "").strip()
    if kind not in _TYPES:
        raise BadRequest("反馈类型不合法")

    body = (content or "").strip()
    if not (1 <= len(body) <= 500):
        raise BadRequest("反馈正文需为 1–500 字")

    if kind == "dish":
        if dish_id is None:
            raise BadRequest("菜品反馈需指定菜品")
        dish = session.get(Dish, dish_id)
        on_sale = dish is not None and bool(
            session.scalar(
                select(func.count())
                .select_from(DishLocation)
                .where(DishLocation.dish_id == dish_id, DishLocation.activate.is_(True))
            )
        )
        if not on_sale:
            raise NotFound("菜品不存在或已下架")
    else:
        if dish_id is not None:
            raise BadRequest("产品反馈不应关联菜品")
        dish_id = None

    check_text(body)
    repo.add(session, type_=kind, dish_id=dish_id, user_id=student_id, content=body)
    # 事务边界在 service：返回响应前落库，避免「提示已收到但未入库」
    session.commit()
