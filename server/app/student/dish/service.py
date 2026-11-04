"""dish 域业务规则（docs/func/client/A-浏览与发现/A1-搜索找菜.md）。"""

from __future__ import annotations

import hashlib
import re

from sqlalchemy.orm import Session

from app.core.config import embedding_is_remote, settings
from app.core.runtime.cache import ttl_cached
from app.core.search import rerank
from app.core.search.embedding import cosine, current_model, embed
from app.core.errors import BadRequest
from app.core.schemas import OutModel
from app.core.storage import to_absolute
from app.student.dish import repo

_MAX_RESULTS = 30
_TTL_SECONDS = 30.0
_QUERY_MAX_LEN = 200


class DishLocationVO(OutModel):
    id: int
    canteen_name: str
    stall_name: str
    floor: str


class DishVO(OutModel):
    id: int
    name: str
    image_url: str | None
    description: str
    price: int | None
    locations: list[DishLocationVO]


class RecommendOut(OutModel):
    dishes: list[DishVO]


def _rows(session: Session) -> list[repo.LocationRow]:
    # 缓存按向量模型分区：切换模型后不复用旧向量行
    return ttl_cached(
        f"student:dishes:on_sale:{current_model()}",
        _TTL_SECONDS,
        lambda: repo.list_on_sale(session, model=current_model()),
    )


def _group_vos(
    rows: list[repo.LocationRow], order: list[int] | None = None
) -> list[DishVO]:
    grouped: dict[int, list[repo.LocationRow]] = {}
    for row in rows:
        grouped.setdefault(row.dish_id, []).append(row)
    ids = order if order is not None else list(grouped)
    vos: list[DishVO] = []
    for dish_id in ids:
        dish_rows = grouped.get(dish_id)
        if not dish_rows:
            continue
        first = dish_rows[0]
        vos.append(
            DishVO(
                id=first.dish_id,
                name=first.name,
                image_url=to_absolute(first.image),
                description=first.description,
                price=first.price,
                locations=[
                    DishLocationVO(
                        id=r.location_id,
                        canteen_name=r.canteen_name,
                        stall_name=r.stall_name,
                        floor=r.floor,
                    )
                    for r in dish_rows
                ],
            )
        )
    return vos


def list_dishes(session: Session) -> list[DishVO]:
    return _group_vos(_rows(session))


_PRICE_RE = re.compile(r"(\d+)\s*(?:元|块)")


def _apply_hard_constraints(
    rows: list[repo.LocationRow], query: str
) -> list[repo.LocationRow]:
    """硬约束只认**完整菜名** / 食堂名 / 价格上限三类；按位置行过滤。"""
    canteen_names = {row.canteen_name for row in rows}
    hit_canteens = {name for name in canteen_names if name and name in query}
    hit_names = {
        row.name for row in rows if row.name and len(row.name) >= 2 and row.name in query
    }
    price_match = _PRICE_RE.search(query)
    max_cents = int(price_match.group(1)) * 100 if price_match else None

    result: list[repo.LocationRow] = []
    for row in rows:
        if hit_names and row.name not in hit_names:
            continue
        if hit_canteens and row.canteen_name not in hit_canteens:
            continue
        if max_cents is not None and (row.price is None or row.price > max_cents):
            continue
        result.append(row)
    return result


def _row_vector(row: repo.LocationRow) -> list[float] | None:
    """向量已按当前模型 join 出来；外部模型下缺失则跳过，不现场补算。"""
    if row.embedding:
        return row.embedding
    if embedding_is_remote():
        return None
    return embed(row.description or row.name)


def _reranked(query: str, rows: list[repo.LocationRow]) -> list[repo.LocationRow]:
    """System 1 精排：未启用 / 失败 / 候选不足时原样返回（🔴 精排故障不升级为 500）。"""
    if not rerank.enabled() or len(rows) < 2:
        return rows
    top_k = settings.system1_top_k
    order = rerank.rerank(query, [row.description or row.name for row in rows], top_k)
    if order is None:
        return rows
    return [rows[index] for index in order if 0 <= index < len(rows)][:top_k]


def recommend(session: Session, *, query: str) -> list[DishVO]:
    """一句话匹配：硬约束过滤 + 向量余弦排序（+ 可选精排）。"""
    text = (query or "").strip()
    if not text:
        raise BadRequest("请输入想吃的菜")
    if len(text) > _QUERY_MAX_LEN:
        raise BadRequest(f"查询文本不能超过 {_QUERY_MAX_LEN} 字")

    digest = hashlib.md5(text.encode("utf-8")).hexdigest()
    key = f"student:dishes:recommend:{current_model()}:{digest}"
    return ttl_cached(key, _TTL_SECONDS, lambda: _search(session, text))


def _search(session: Session, text: str) -> list[DishVO]:
    rows = _apply_hard_constraints(_rows(session), text)
    if not rows:
        return []

    # 一道菜多位置共享向量：按 dish 去重后打分，命中位置随之收窄
    by_dish: dict[int, repo.LocationRow] = {}
    for row in rows:
        by_dish.setdefault(row.dish_id, row)

    query_vec = embed(text)
    scored: list[tuple[float, int, int]] = []
    for index, (dish_id, row) in enumerate(by_dish.items()):
        vector = _row_vector(row)
        if vector is None:
            continue
        scored.append((cosine(query_vec, vector), index, dish_id))
    if not scored:
        return []

    scored.sort(key=lambda item: (-item[0], item[1]))
    ordered_ids = [item[2] for item in scored[:_MAX_RESULTS]]
    reps = [by_dish[dish_id] for dish_id in ordered_ids]
    reranked = _reranked(text, reps)
    if not reranked:
        return []
    ordered = [row.dish_id for row in reranked]
    allowed = set(ordered)
    hit_rows = [row for row in rows if row.dish_id in allowed]
    return _group_vos(hit_rows, ordered)
