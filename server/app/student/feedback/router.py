"""学生端反馈端点（docs/api/client/feedback.md）。

    POST /feedback   🔑 提交产品 / 菜品文本反馈
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import Field
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, ok
from app.core.runtime.ratelimit import RULE_FEEDBACK, check
from app.core.schemas import InModel
from app.student.feedback import service

router = APIRouter(tags=["feedback"])


class FeedbackIn(InModel):
    """正文上限与业务规则（1–500 字）一致：超长在参数层即 `400`，不把整包读进内存。"""

    type: str = Field(min_length=1, max_length=8)
    dish_id: int | None = None
    content: str = Field(min_length=1, max_length=500)


@router.post("/feedback", response_model=Envelope[None], summary="提交文本反馈")
def submit_feedback(
    payload: FeedbackIn,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[None]:
    check(f"feedback:u:{student_id}", RULE_FEEDBACK)
    service.submit(
        session,
        student_id=student_id,
        type_=payload.type,
        dish_id=payload.dish_id,
        content=payload.content,
    )
    return ok(None)
