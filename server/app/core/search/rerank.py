"""System 1 精排（可选）：用 rerank 模型对向量召回的候选重排。

🔴 **不是硬依赖**：未启用 / 超时 / 调用失败一律返回 ``None``，调用方保持向量排序，
不把精排故障升级成 500。
"""

from __future__ import annotations

import httpx

from app.core.config import settings


def enabled() -> bool:
    """是否启用精排（``SYSTEM1_ENABLED=true`` 且角色为 ``rerank``）。"""
    return bool(settings.system1_enabled) and settings.system1_role.strip().lower() == "rerank"


def rerank(query: str, documents: list[str], top_k: int) -> list[int] | None:
    """按相关度降序返回**原始下标**；失败返回 ``None``（调用方退回向量排序）。"""
    if not documents:
        return []

    base = settings.system1_base_url.strip().rstrip("/")
    payload = {
        "model": settings.system1_model.strip(),
        "query": query,
        "documents": documents,
        "top_n": min(top_k, len(documents)),
        "return_documents": False,
    }
    headers = {"Authorization": f"Bearer {settings.system1_api_key.strip()}"}
    try:
        with httpx.Client(timeout=settings.system1_timeout) as client:
            resp = client.post(f"{base}/rerank", json=payload, headers=headers)
            resp.raise_for_status()
            results = resp.json()["results"]
        order = sorted(results, key=lambda item: -float(item["relevance_score"]))
        return [int(item["index"]) for item in order]
    except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError):
        return None
