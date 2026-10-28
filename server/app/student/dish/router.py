"""学生端「美食地图」单页：美食地图。

端点（docs/api/client/dishes.md）：
    GET  /dishes                  🔑 需登录，全量在售菜品（端上随机散布、可平移缩放）
    POST /dishes/{id}/likes       🔑 需登录，两态点赞 toggle
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, ok
from app.core.ratelimit import RULE_LIKE, RULE_READ_ACCOUNT, check
from app.student.dish import service
from app.student.dish.service import DishLikeOut, DishVO

router = APIRouter(tags=["dish"])


@router.get("/dishes", response_model=Envelope[list[DishVO]], summary="美食地图全量在售菜品")
def list_dishes(
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[list[DishVO]]:
    check(f"read:u:{student_id}", RULE_READ_ACCOUNT)
    return ok(service.list_for_map(session, student_id=student_id))


@router.post(
    "/dishes/{dish_id}/likes",
    response_model=Envelope[DishLikeOut],
    summary="两态点赞 toggle",
)
def toggle_like(
    dish_id: int,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[DishLikeOut]:
    check(f"like:u:{student_id}", RULE_LIKE)
    return ok(service.toggle_like(session, student_id=student_id, dish_id=dish_id))
