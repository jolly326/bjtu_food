"""feedback 域数据访问。"""

from __future__ import annotations

from sqlalchemy.orm import Session

from app.models import Feedback


def add(
    session: Session, *, type_: str, dish_id: int | None, user_id: int, content: str
) -> Feedback:
    row = Feedback(type=type_, dish_id=dish_id, user_id=user_id, content=content)
    session.add(row)
    session.flush()
    return row
