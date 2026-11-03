"""学生端反馈端点（docs/api/client/feedback.md）。

    POST /feedback   🔑 提交产品 / 菜品文本反馈
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, ok
from app.core.ratelimit import RULE_FEEDBACK, check
from app.core.schemas import InModel
from app.student.feedback import service

router = APIRouter(tags=["feedback"])


class FeedbackIn(InModel):
    type: str
    dish_id: int | None = None
    content: str


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
