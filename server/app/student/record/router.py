"""学生端「记忆」tab：饮食记录的创建 / 评分 / 取消 / 列表。

端点（docs/api/client/records.md）：
    POST   /my/records          去品尝（幂等）
    PATCH  /my/records/{id}     三态评分（一次性）
    DELETE /my/records/{id}     取消品尝（仅未评分）
    GET    /my/records          我的食谱（全量，按 wantedAt 降序）

🔴 全部要求登录 + **归属校验**：非本人记录与「不存在」同一响应 4001。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, ok
from app.core.ratelimit import RULE_REACT, RULE_UNWANT, RULE_WANT, check
from app.core.schemas import InModel, OutModel
from app.student.record import service

router = APIRouter(prefix="/my", tags=["record"])


class WantIn(InModel):
    dish_id: int


class ReactIn(InModel):
    reaction: int


class RecordOut(OutModel):
    id: int
    dish_id: int
    name: str
    image_url: str | None = None
    wanted_at: str
    reaction: int | None = None
    flavor_level: str | None = None


def _out(record) -> RecordOut:
    return RecordOut(
        id=record.id,
        dish_id=record.dish_id,
        name=record.name,
        image_url=record.image_url,
        wanted_at=record.wanted_at,
        reaction=record.reaction,
        flavor_level=record.flavor_level,
    )


@router.post("/records", response_model=Envelope[RecordOut], summary="去品尝")
def create_record(
    payload: WantIn,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[RecordOut]:
    check(f"want:u:{student_id}", RULE_WANT)
    record = service.create_record(session, student_id=student_id, dish_id=payload.dish_id)
    return ok(_out(record))


@router.patch("/records/{record_id}", response_model=Envelope[RecordOut], summary="三态评分")
def react_record(
    record_id: int,
    payload: ReactIn,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[RecordOut]:
    check(f"react:u:{student_id}", RULE_REACT)
    record = service.react(session, student_id=student_id, record_id=record_id, reaction=payload.reaction)
    return ok(_out(record))


@router.delete("/records/{record_id}", response_model=Envelope[None], summary="取消品尝")
def cancel_record(
    record_id: int,
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[None]:
    check(f"unwant:u:{student_id}", RULE_UNWANT)
    service.cancel(session, student_id=student_id, record_id=record_id)
    return ok(None)


@router.get("/records", response_model=Envelope[list[RecordOut]], summary="我的食谱")
def list_records(
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[list[RecordOut]]:
    records = service.list_mine(session, student_id=student_id)
    return ok([_out(r) for r in records])
