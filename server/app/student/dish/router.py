"""学生端「美食地图」单页：美食地图。

端点（docs/api/client/dishes.md）：
    GET  /dishes                  🔑 需登录，全量在售菜品（端上错位密铺、可平移缩放）
    POST /dishes/{id}/likes       🔑 需登录，两态点赞 toggle
"""

from __future__ import annotations

import hashlib
import json

from fastapi import APIRouter, Depends, Header, Response
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
    response: Response,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
    if_none_match: str | None = Header(default=None, alias="If-None-Match"),
) -> Envelope[list[DishVO]] | Response:
    check(f"read:u:{student_id}", RULE_READ_ACCOUNT)
    vos = service.list_for_map(session, student_id=student_id)
    payload = [vo.model_dump(mode="json") for vo in vos]
    etag = (
        '"'
        + hashlib.md5(
            json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
        ).hexdigest()
        + '"'
    )
    if if_none_match and if_none_match == etag:
        return Response(status_code=304, headers={"ETag": etag, "Cache-Control": "private, max-age=30"})
    response.headers["ETag"] = etag
    response.headers["Cache-Control"] = "private, max-age=30"
    return ok(vos)


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
