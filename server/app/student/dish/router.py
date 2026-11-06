"""学生端菜品端点（docs/api/client/dishes.md）。

    GET  /dishes              🔑 全量在售菜品（无条件结果集渲染）
    POST /dishes/recommend    🔑 一句话匹配（硬约束 + 向量召回）
"""

from __future__ import annotations

import hashlib
import json

from fastapi import APIRouter, Depends, Header, Response
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, ok
from app.core.runtime.ratelimit import RULE_READ_ACCOUNT, RULE_RECOMMEND, check
from app.core.schemas import InModel
from app.student.dish import service
from app.student.dish.service import DishVO, RecommendOut

router = APIRouter(tags=["dish"])


class RecommendIn(InModel):
    query: str


@router.get("/dishes", response_model=Envelope[list[DishVO]], summary="全量在售菜品")
def list_dishes(
    response: Response,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
    if_none_match: str | None = Header(default=None, alias="If-None-Match"),
) -> Envelope[list[DishVO]] | Response:
    check(f"read:u:{student_id}", RULE_READ_ACCOUNT)
    vos = service.list_dishes(session)
    payload = [vo.model_dump(mode="json") for vo in vos]
    etag = (
        '"'
        + hashlib.md5(
            json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
        ).hexdigest()
        + '"'
    )
    if if_none_match and if_none_match == etag:
        return Response(
            status_code=304,
            headers={"ETag": etag, "Cache-Control": "private, max-age=30"},
        )
    response.headers["ETag"] = etag
    response.headers["Cache-Control"] = "private, max-age=30"
    return ok(vos)


@router.post("/dishes/recommend", response_model=Envelope[RecommendOut], summary="一句话匹配菜品")
def recommend(
    payload: RecommendIn,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[RecommendOut]:
    check(f"recommend:u:{student_id}", RULE_RECOMMEND)
    dishes = service.recommend(session, query=payload.query)
    return ok(RecommendOut(dishes=dishes))
