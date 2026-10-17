"""运营端海报业务规则（docs/func/web/A4-海报管理.md、docs/schema/poster.md）。

* ``sort`` 是**全局序**：小者靠前，同值按创建顺序（与学生端橱窗同序）
* 新建 ``sort`` 缺省 ⇒ 取当前全局最大值 + 1（排到最后）；编辑缺省 ⇒ 保持原值
* ``status`` 只允许 ``on`` / ``off``；🔴 ``off`` 对学生端等价于不存在
* ``image`` **必填** —— 无「空海报」态，移除 = 删除海报
* 删除档口时海报由外键 **ON DELETE CASCADE** 连带清理（本域无感知）
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.poster import repo
from app.admin.poster.repo import PosterRow
from app.core.errors import BadRequest, NotFound
from app.core.storage import to_relative
from app.models import Poster, Stall

VALID_STATUS = frozenset({"on", "off"})


def _validate(payload) -> None:
    if payload.status not in VALID_STATUS:
        raise BadRequest("状态只能取 on（上橱窗）/ off（不展示）")


def _image(payload) -> str:
    """绝对 URL → 入库相对路径；无图直接拒绝（海报无「空图」态）。"""
    image = to_relative((payload.image_url or "").strip())
    if not image:
        raise BadRequest("海报图不能为空")
    return image


def list_page(
    session: Session,
    *,
    stall_id: int | None,
    canteen_id: int | None,
    status: str | None,
    page: int,
    size: int,
) -> tuple[list[PosterRow], int]:
    return repo.list_page(
        session,
        stall_id=stall_id,
        canteen_id=canteen_id,
        status=status,
        page=page,
        size=size,
    )


def create(session: Session, *, payload) -> PosterRow:
    _validate(payload)
    if session.get(Stall, payload.stall_id) is None:
        raise NotFound("档口不存在")

    sort = payload.sort
    if sort is None:
        # 缺省排到当前全局最后（空表首张为 1）
        sort = int(session.scalar(select(func.max(Poster.sort)).select_from(Poster)) or 0) + 1

    poster = Poster(
        stall_id=payload.stall_id,
        image=_image(payload),
        sort=sort,
        status=payload.status,
    )
    session.add(poster)
    session.flush()
    return _reload(session, poster.id)


def update(session: Session, *, poster_id: int, payload) -> PosterRow:
    _validate(payload)
    poster = session.get(Poster, poster_id)
    if poster is None:
        raise NotFound("海报不存在")
    if session.get(Stall, payload.stall_id) is None:
        raise NotFound("档口不存在")

    poster.stall_id = payload.stall_id
    poster.image = _image(payload)
    poster.status = payload.status
    if payload.sort is not None:
        poster.sort = payload.sort
    session.flush()
    return _reload(session, poster.id)


def delete(session: Session, *, poster_id: int) -> None:
    poster = session.get(Poster, poster_id)
    if poster is None:
        raise NotFound("海报不存在")
    # 只删库记录；COS 图片文件不回收（与菜品图同口径）
    session.delete(poster)
    session.flush()


def _reload(session: Session, poster_id: int) -> PosterRow:
    row = repo.find_row(session, poster_id=poster_id)
    if row is None:
        raise NotFound("海报不存在")
    return row