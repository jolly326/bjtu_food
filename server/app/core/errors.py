"""统一错误码与业务异常。

错误码口径（见 docs/api/README.md「业务状态码」）：

==== ======= ==========================================
code 含义     端上处置
==== ======= ==========================================
200  成功
400  参数 / 业务校验失败
401  未登录 / token 失效
403  无权限（管理端）
429  请求过于频繁
4001 资源不存在（含「非本人」，与不存在同响应）
500  服务器异常
==== ======= ==========================================

注意：**没有** 4031（邮箱认证已整体删除）。
"""

from __future__ import annotations

CODE_OK = 200
CODE_BAD_REQUEST = 400
CODE_UNAUTHORIZED = 401
CODE_FORBIDDEN = 403
CODE_RATE_LIMITED = 429
CODE_NOT_FOUND = 4001
CODE_INTERNAL = 500


class ApiError(Exception):
    """业务异常；由 main.py 的异常处理器翻译成统一信封。"""

    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class BadRequest(ApiError):
    def __init__(self, message: str = "参数不合法") -> None:
        super().__init__(CODE_BAD_REQUEST, message)


class Unauthorized(ApiError):
    def __init__(self, message: str = "未登录或凭证已失效") -> None:
        super().__init__(CODE_UNAUTHORIZED, message)


class Forbidden(ApiError):
    def __init__(self, message: str = "无权限") -> None:
        super().__init__(CODE_FORBIDDEN, message)


class RateLimited(ApiError):
    def __init__(self, message: str = "操作过于频繁，请稍后再试") -> None:
        super().__init__(CODE_RATE_LIMITED, message)


class NotFound(ApiError):
    """资源不存在，或非本人访问他人资源。

    🔴 两者返回**同一响应**，用于避免「ID 枚举探测」。
    """

    def __init__(self, message: str = "资源不存在") -> None:
        super().__init__(CODE_NOT_FOUND, message)
