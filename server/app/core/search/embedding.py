"""文本向量：本地确定性实现 / 外部 HTTP 向量服务，按 ``EMBEDDING_PROVIDER`` 切换。

* ``local``（默认）⇒ **字符 bigram 哈希**到固定维度并 L2 归一化：零依赖、确定性、可离线；
  模型标识 ``LOCAL_MODEL``。
* ``http`` ⇒ 调用 **OpenAI 兼容**的 ``POST {EMBEDDING_BASE_URL}/embeddings``
  （如硅基流动 ``BAAI/bge-m3``），返回向量同样 L2 归一化；模型标识取 ``EMBEDDING_MODEL``。

两种 provider 的向量**互不通用**，因此库里 ``dish.embedding_model`` 必须等于
:func:`current_model` 才会被检索使用。
"""

from __future__ import annotations

import hashlib
import math

import httpx

from app.core.config import embedding_is_remote, settings
from app.core.errors import InternalError

LOCAL_MODEL = "local-hash-bigram-v1"
DIM = 1024


def current_model() -> str:
    """当前生效的向量模型标识（写入 ``dish.embedding_model``；缓存键也按它分区）。"""
    if embedding_is_remote():
        return settings.embedding_model.strip()
    return LOCAL_MODEL


def embed(text: str) -> list[float]:
    """把文本编码为 L2 归一化的定长向量；空文本返回全 0。

    外部服务失败 / 超时 ⇒ :class:`InternalError`（HTTP 500，端上提示稍后再试）。
    """
    t = (text or "").strip()
    if not t:
        return [0.0] * DIM
    if embedding_is_remote():
        vector = _normalize(_http_embed(t))
        if settings.embedding_dim > 0 and len(vector) != settings.embedding_dim:
            raise InternalError(
                f"向量维度与配置不一致（期望 {settings.embedding_dim}，实际 {len(vector)}）"
            )
        return vector
    return _local_embed(t)


def cosine(a: list[float] | None, b: list[float] | None) -> float:
    """余弦相似度（向量已归一化 ⇒ 等价点积）；任一为空返回 0。"""
    if not a or not b:
        return 0.0
    n = min(len(a), len(b))
    return float(sum(a[i] * b[i] for i in range(n)))


def _normalize(vec: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in vec)) or 1.0
    return [round(x / norm, 6) for x in vec]


def _local_embed(text: str) -> list[float]:
    """字符 bigram 哈希：同一文本恒得同一向量，无需外部依赖。"""
    t = text.lower()
    vec = [0.0] * DIM
    grams = [t[i : i + 2] for i in range(len(t) - 1)] if len(t) > 1 else [t]
    for g in grams:
        h = int(hashlib.md5(g.encode("utf-8")).hexdigest(), 16)
        idx = h % DIM
        sign = 1.0 if (h >> 8) & 1 else -1.0
        vec[idx] += sign
    return _normalize(vec)


def _http_embed(text: str) -> list[float]:
    """OpenAI 兼容 embeddings 调用（硅基流动 / 百炼 / 自建 vLLM 同协议）。"""
    base = settings.embedding_base_url.strip().rstrip("/")
    payload = {"model": settings.embedding_model.strip(), "input": text}
    headers = {"Authorization": f"Bearer {settings.embedding_api_key.strip()}"}
    try:
        with httpx.Client(timeout=settings.embedding_timeout) as client:
            resp = client.post(f"{base}/embeddings", json=payload, headers=headers)
            resp.raise_for_status()
            vector = [float(x) for x in resp.json()["data"][0]["embedding"]]
    except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError) as exc:
        raise InternalError("语义检索服务暂不可用，请稍后再试") from exc
    return vector
