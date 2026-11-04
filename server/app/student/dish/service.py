"""dish 域业务规则（docs/func/client/A-浏览与发现/A1-搜索找菜.md）。"""

from __future__ import annotations

import hashlib
import re
from dataclasses import replace

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
    offering_id: int
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
    # 缓存按向量模型分区：切换模型后不复用旧向量行
    return ttl_cached(
        f"student:dishes:on_sale:{current_model()}",
        _TTL_SECONDS,
        lambda: repo.list_on_sale(session),
    )


def _to_vo(row: repo.DishRow) -> DishVO:
    return DishVO(
        id=row.id,
        name=row.name,
        image_url=to_absolute(row.image),
        description=row.description,
        locations=[
            DishLocationVO(
                offering_id=loc.offering_id,
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


def _apply_hard_constraints(rows: list[repo.DishRow], query: str) -> list[repo.DishRow]:
    """硬约束只认**完整菜名** / 食堂名 / 价格上限三类；其余交给向量相似度。

    - 菜名命中 ⇒ 只保留命中菜名的菜品；
    - 食堂 / 价格命中 ⇒ 只保留符合条件的**售卖点**，返回行的 ``locations`` 同步收窄；
    - 三类都未命中 ⇒ 不过滤，全部交给余弦排序。
    """
    canteen_names = {loc.canteen_name for row in rows for loc in row.locations}
    hit_canteens = {name for name in canteen_names if name and name in query}
    # 单字菜名（如「粥」）出现在任意句子里会造成过度收窄，只认 ≥2 字的完整菜名
    hit_names = {row.name for row in rows if row.name and len(row.name) >= 2 and row.name in query}

    price_match = _PRICE_RE.search(query)
    max_cents = int(price_match.group(1)) * 100 if price_match else None
    filter_locations = bool(hit_canteens) or max_cents is not None

    result: list[repo.DishRow] = []
    for row in rows:
        if hit_names and row.name not in hit_names:
            continue
        locations = row.locations
        if hit_canteens:
            locations = [loc for loc in locations if loc.canteen_name in hit_canteens]
        if max_cents is not None:
            locations = [
                loc for loc in locations if loc.price is not None and loc.price <= max_cents
            ]
        if filter_locations and not locations:
            continue
        result.append(replace(row, locations=locations) if filter_locations else row)
    return result


def _row_vector(row: repo.DishRow) -> list[float] | None:
    """只信任与当前模型一致的预计算向量。

    * 本地模型：缺失 / 不符时实时补算（零成本）；
    * 外部向量服务：**跳过该行**（返回 ``None``），由回填脚本补齐 ——
      否则一次搜索会打 N 次外部请求。
    """
    if row.embedding and row.embedding_model == current_model():
        return row.embedding
    if embedding_is_remote():
        return None
    return embed(row.description or row.name)


def _reranked(query: str, rows: list[repo.DishRow]) -> list[repo.DishRow]:
    """System 1 精排：未启用 / 失败 / 候选不足时原样返回（🔴 精排故障不升级为 500）。"""
    if not rerank.enabled() or len(rows) < 2:
        return rows
    top_k = settings.system1_top_k
    order = rerank.rerank(query, [row.description or row.name for row in rows], top_k)
    if order is None:
        return rows
    picked = [rows[index] for index in order if 0 <= index < len(rows)]
    return picked[: settings.system1_top_k]


def recommend(session: Session, *, query: str) -> list[DishVO]:
    """一句话匹配：硬约束过滤 + 向量余弦排序（+ 可选精排）。

    同一句话 30 秒内复用结果：键前缀与「在售菜品」一致，后台写操作会把两者一起失效，
    避免重复付费调用向量 / 精排服务。
    """
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

    query_vec = embed(text)
    scored: list[tuple[float, int, repo.DishRow]] = []
    for index, row in enumerate(rows):
        vector = _row_vector(row)
        if vector is None:
            continue  # 外部模型下未回填的行不参与召回
        scored.append((cosine(query_vec, vector), index, row))
    if not scored:
        return []

    # 相似度降序；同分保持原始顺序（稳定）
    scored.sort(key=lambda item: (-item[0], item[1]))
    hits = _reranked(text, [item[2] for item in scored[:_MAX_RESULTS]])
    return [_to_vo(row) for row in hits]
