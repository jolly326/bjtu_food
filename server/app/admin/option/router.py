"""运营端纠错选项字典（docs/func/web/B3-纠错选项管理.md、docs/api/web/issues.md）。

端点：
    GET    /admin/dish-issue-options          全量（不分页）+ 引用次数
    POST   /admin/dish-issue-options          新建
    PUT    /admin/dish-issue-options/{id}     改名（全站生效）
    DELETE /admin/dish-issue-options/{id}     删除（被引用即禁删）
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.admin.option import service
from app.core.db import get_session
from app.core.deps import current_admin_id
from app.core.envelope import Envelope, ok
from app.core.schemas import InModel, OutModel

router = APIRouter(prefix="/admin", tags=["admin-option"])


class OptionSaveIn(InModel):
    name: str


class OptionOut(OutModel):
    id: int
    name: str
    ref_count: int
    updated_at: str


@router.get(
    "/dish-issue-options", response_model=Envelope[list[OptionOut]], summary="纠错选项字典"
)
def list_options(
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[list[OptionOut]]:
    rows = service.list_all(session)
    return ok(
        [OptionOut(id=r.id, name=r.name, ref_count=r.ref_count, updated_at=r.updated_at) for r in rows]
    )


@router.post("/dish-issue-options", response_model=Envelope[OptionOut], summary="新建选项")
def create_option(
    payload: OptionSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[OptionOut]:
    row = service.create(session, name=payload.name)
    return ok(OptionOut(id=row.id, name=row.name, ref_count=row.ref_count, updated_at=row.updated_at))


@router.put("/dish-issue-options/{option_id}", response_model=Envelope[OptionOut], summary="改选项文案")
def update_option(
    option_id: int,
    payload: OptionSaveIn,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[OptionOut]:
    row = service.update(session, option_id=option_id, name=payload.name)
    return ok(OptionOut(id=row.id, name=row.name, ref_count=row.ref_count, updated_at=row.updated_at))


@router.delete("/dish-issue-options/{option_id}", response_model=Envelope[None], summary="删除选项")
def delete_option(
    option_id: int,
    admin_id: int = Depends(current_admin_id),
    session: Session = Depends(get_session),
) -> Envelope[None]:
    service.delete(session, option_id=option_id)
    return ok(None)
