"""进程内固定窗口限频（docs/secur/S-03）。

口径：

* 阈值见下方规则表，键 = 「端点 + 维度 + 主体」
* 账号维度限频在端点内（鉴权之后）；全局 IP 兜底在 HTTP 层中间件（探活端点不计数）
* 命中 ⇒ ``429`` + 可等待秒数（端上做退避倒计时）
* 🔴 存储为**进程内**：单 worker 部署下计数天然唯一；
  **一旦加 worker 或扩实例必须下沉共享存储**，否则实际阈值 × 进程数
"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass

from app.core.errors import RateLimited

PRUNE_INTERVAL = 300  # 秒：过期桶的清理周期


@dataclass(frozen=True)
class Rule:
    limit: int
    window: int  # 秒


# ---------- 规则表（docs/secur/S-03 的「限频矩阵」） ----------
RULE_LIKE = Rule(60, 3600)          # POST /dishes/{id}/likes（两态点赞 toggle）
RULE_ISSUE = Rule(10, 3600)         # POST /dishes/{id}/issues（纠错）
RULE_READ_ACCOUNT = Rule(300, 3600)  # 读端点（账号维度，四个读端点共享同一配额）
RULE_READ_IP = Rule(60, 60)         # 全局 IP 兜底（HTTP 层中间件）
RULE_WECHAT_LOGIN = Rule(20, 3600)  # POST /auth/wechat-login
RULE_ADMIN_LOGIN = Rule(10, 60)     # POST /admin/sessions


class FixedWindowCounter:
    """固定窗口计数器（线程安全，单进程）。"""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._buckets: dict[str, tuple[float, int]] = {}
        self._last_prune = time.time()

    def _prune(self, now: float) -> None:
        if now - self._last_prune < PRUNE_INTERVAL:
            return
        self._last_prune = now
        expired = [k for k, (start, _) in self._buckets.items() if now - start > 7200]
        for k in expired:
            self._buckets.pop(k, None)

    def hit(self, key: str, rule: Rule) -> tuple[bool, int]:
        """返回 (是否放行, 需等待秒数)。"""
        now = time.time()
        with self._lock:
            self._prune(now)
            start, count = self._buckets.get(key, (now, 0))
            if now - start >= rule.window:
                start, count = now, 0
            count += 1
            self._buckets[key] = (start, count)
            if count > rule.limit:
                return False, max(1, int(rule.window - (now - start)) + 1)
            return True, 0

    def reset(self) -> None:
        """清空全部计数（端点级用例之间互不干扰）。"""
        with self._lock:
            self._buckets.clear()
            self._last_prune = time.time()


limiter = FixedWindowCounter()


def retry_after(key: str, rule: Rule) -> int:
    """返回需等待秒数；``0`` = 放行。

    中间件在鉴权之前生效，不走异常处理器，故单独给一个「只取结果」的入口。
    """
    allowed, wait = limiter.hit(key, rule)
    return 0 if allowed else wait


def check(key: str, rule: Rule) -> None:
    wait = retry_after(key, rule)
    if wait:
        raise RateLimited(f"操作过于频繁，请 {wait} 秒后再试")


def client_ip(request: object) -> str:
    """取请求来源 IP（取不到时用占位，保证限频键稳定）。"""
    headers = getattr(request, "headers", {}) or {}
    forwarded = headers.get("x-forwarded-for") if hasattr(headers, "get") else None
    if forwarded:
        return str(forwarded).split(",", 1)[0].strip()
    client = getattr(request, "client", None)
    host = getattr(client, "host", None) if client else None
    return host or "unknown"
