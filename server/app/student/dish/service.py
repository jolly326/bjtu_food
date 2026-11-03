"""dish 域业务规则（docs/func/client/A-浏览与发现/A1-搜索找菜.md）。"""

from __future__ import annotations

import re

from sqlalchemy.orm import Session

from app.core.cache import ttl_cached
from app.core.embedding import cosine, embed
from app.core.errors import BadRequest
from app.core.schemas import OutModel
from app.core.storage import to_absolute
from app.student.dish import repo

_MAX_RESULTS = 30
_TTL_SECONDS = 30.0


class DishLocationVO(OutModel):
    canteen_name: str
    stall_name: str
    floor: str
    price: int | None


class DishVO(OutModel):
    id: int
    name: str
    image_url: str | None
    description: str
    locations: list[DishLocationVO]


class RecommendOut(OutModel):
    dishes: list[DishVO]


def _rows(session: Session) -> list[repo.DishRow]:
    return ttl_cached(
        "student:dishes:on_sale", _TTL_SECONDS, lambda: repo.list_on_sale(session)
    )


def _to_vo(row: repo.DishRow) -> DishVO:
    return DishVO(
        id=row.id,
        name=row.name,
        image_url=to_absolute(row.image),
        description=row.description,
        locations=[
            DishLocationVO(
                canteen_name=loc.canteen_name,
                stall_name=loc.stall_name,
                floor=loc.floor,
                price=loc.price,
            )
            for loc in row.locations
        ],
    )


def list_dishes(session: Session) -> list[DishVO]:
    return [_to_vo(row) for row in _rows(session)]


_PRICE_RE = re.compile(r"(\d+)\s*(?:元|块)")


def _apply_hard_filters(rows: list[repo.DishRow], query: str) -> list[repo.DishRow]:
    """硬约束只认食堂名与价格区间；其余交给向量相似度。"""
    canteen_names = {loc.canteen_name for row in rows for loc in row.locations}
    hit_canteens = {name for name in canteen_names if name and name in query}

    price_match = _PRICE_RE.search(query)
    max_cents = int(price_match.group(1)) * 100 if price_match else None

    result: list[repo.DishRow] = []
    for row in rows:
        locations = row.locations
        if hit_canteens:
            locations = [loc for loc in locations if loc.canteen_name in hit_canteens]
        if max_cents is not None:
            locations = [
                loc for loc in locations if loc.price is not None and loc.price <= max_cents
            ]
        if (hit_canteens or max_cents is not None) and not locations:
            continue
        result.append(row)
    return result


def recommend(session: Session, *, query: str) -> list[DishVO]:
    """一句话匹配：硬约束过滤 + 向量余弦排序。"""
    text = (query or "").strip()
    if not text:
        raise BadRequest("请输入想吃的菜")
    if len(text) > 200:
        raise BadRequest("查询文本不能超过 200 字")

    rows = _apply_hard_filters(_rows(session), text)
    if not rows:
        return []

    query_vec = embed(text)
    scored = [
        (
            cosine(query_vec, row.embedding or embed(row.description or row.name)),
            index,
            row,
        )
        for index, row in enumerate(rows)
    ]
    # 相似度降序；同分保持原始顺序（稳定）
    scored.sort(key=lambda item: (-item[0], item[1]))
    return [_to_vo(item[2]) for item in scored[:_MAX_RESULTS]]
