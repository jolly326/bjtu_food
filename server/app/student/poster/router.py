"""学生端「风物」tab（橱窗）：竖版海报列表。

端点（docs/api/client/posters.md）：
    GET /posters   🔑 需登录，固定顺序（poster.sort 升序，仅 status='on'）
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, ok
from app.core.ratelimit import RULE_READ_ACCOUNT, check
from app.core.schemas import OutModel
from app.core.storage import to_absolute
from app.student.poster import service

router = APIRouter(tags=["poster"])


class PosterOut(OutModel):
    id: int
    image_url: str


@router.get("/posters", response_model=Envelope[list[PosterOut]], summary="档口海报列表")
def list_posters(
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[list[PosterOut]]:
    """只返回**上架**海报；顺序 = 运营排定的固定序（不随机）。"""
    check(f"read:u:{student_id}", RULE_READ_ACCOUNT)
    rows = service.list_posters(session)
    return ok([PosterOut(id=row.id, image_url=to_absolute(row.image) or "") for row in rows])
