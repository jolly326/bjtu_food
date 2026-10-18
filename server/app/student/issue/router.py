"""学生端菜品纠错。

端点（docs/api/client/issues.md）：
    GET  /dish-issue-options          选项字典（只读）
    POST /dishes/{id}/issues          提交纠错（只含一个选项）

🔴 学生侧**无文字、无配图** —— 不涉内容安全送检。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, ok
from app.core.ratelimit import RULE_ISSUE, RULE_READ_ACCOUNT, check
from app.core.schemas import InModel, OutModel
from app.student.issue import service

router = APIRouter(tags=["issue"])


class IssueOptionOut(OutModel):
    id: int
    name: str


class IssueIn(InModel):
    option_id: int


@router.get("/dish-issue-options", response_model=Envelope[list[IssueOptionOut]], summary="纠错选项字典")
def list_options(
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[list[IssueOptionOut]]:
    check(f"read:u:{student_id}", RULE_READ_ACCOUNT)
    options = service.list_options(session)
    return ok([IssueOptionOut(id=o.id, name=o.name) for o in options])


@router.post("/dishes/{dish_id}/issues", response_model=Envelope[None], summary="提交菜品纠错")
def submit_issue(
    dish_id: int,
    payload: IssueIn,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[None]:
    check(f"issue:u:{student_id}", RULE_ISSUE)
    service.submit(session, student_id=student_id, dish_id=dish_id, option_id=payload.option_id)
    return ok(None)
