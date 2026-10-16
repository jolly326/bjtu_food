"""学生端食堂字典：推荐页右上角筛选的选项来源。

端点（docs/api/client/canteens.md）：
    GET /canteens   🔑 需登录，全量、按 id 升序
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, ok
from app.core.schemas import OutModel
from app.student.canteen import service

router = APIRouter(tags=["canteen"])


class CanteenOut(OutModel):
    id: int
    name: str


@router.get("/canteens", response_model=Envelope[list[CanteenOut]], summary="食堂字典")
def list_canteens(
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[list[CanteenOut]]:
    rows = service.list_all(session)
    return ok([CanteenOut(id=row.id, name=row.name) for row in rows])
