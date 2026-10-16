"""运营端评分数据业务规则（docs/func/web/B1-反馈查看.md）。

* 排序恒为 ``wantedAt`` 降序（服务端固定，客户端不排）
* ``reaction`` 筛选：``5`` / ``3`` / ``1`` / **``none``**（未评分）；其它值 400
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.admin.record import repo
from app.admin.record.repo import RecordRow
from app.core.errors import BadRequest

VALID_REACTIONS = {"5", "3", "1"}
UNRATED = "none"


@dataclass(slots=True)
class RecordView(RecordRow):
    pass


def _normalize_reaction(reaction: str | None) -> str | None:
    if reaction is None or reaction == "":
        return None
    if reaction in VALID_REACTIONS:
        return reaction
    if reaction == UNRATED:
        return UNRATED
    raise BadRequest("reaction 只能取 5 / 3 / 1 / none")


def list_page(
    session: Session,
    *,
    dish_id: int | None,
    reaction: str | None,
    page: int,
    size: int,
) -> tuple[list[RecordRow], int]:
    normalized = _normalize_reaction(reaction)
    return repo.list_page(session, dish_id=dish_id, reaction=normalized, page=page, size=size)
