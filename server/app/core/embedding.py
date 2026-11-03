"""文本向量（本地确定性实现，可替换为真实语义模型）。

未接入外部 embedding 服务时，用**字符 bigram 哈希**到固定维度并 L2 归一化：
零依赖、确定性、可离线运行。``embedding_model`` 记为 ``LOCAL_MODEL``；
将来接入真实模型只替换本模块的 :func:`embed`，端点与库结构不变。
"""

from __future__ import annotations

import hashlib
import math

LOCAL_MODEL = "local-hash-bigram-v1"
DIM = 1024


def embed(text: str) -> list[float]:
    """把文本编码为 L2 归一化的定长向量（空文本返回全 0）。"""
    t = (text or "").strip().lower()
    vec = [0.0] * DIM
    if not t:
        return vec

    grams = [t[i : i + 2] for i in range(len(t) - 1)] if len(t) > 1 else [t]
    for g in grams:
        h = int(hashlib.md5(g.encode("utf-8")).hexdigest(), 16)
        idx = h % DIM
        sign = 1.0 if (h >> 8) & 1 else -1.0
        vec[idx] += sign

    norm = math.sqrt(sum(x * x for x in vec)) or 1.0
    return [round(x / norm, 6) for x in vec]


def cosine(a: list[float] | None, b: list[float] | None) -> float:
    """余弦相似度（向量已归一化 ⇒ 等价点积）；任一为空返回 0。"""
    if not a or not b:
        return 0.0
    n = min(len(a), len(b))
    return float(sum(a[i] * b[i] for i in range(n)))
