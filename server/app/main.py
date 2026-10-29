"""FastAPI 应用入口。

统一异常处理：把业务异常 / 参数校验异常 / 未捕获异常 / 原生 404-405
都翻译成 ``{code, message, data}`` 信封（HTTP 状态恒 200，业务码在 body 里）。

横切防护：全局 IP 限频兜底 + 请求体上限 + 安全响应头（docs/secur/S-03、S-04 §三）；
接口文档只在非生产模式暴露（docs/secur/S-04 §二）；
启动预检 fail-fast：生产缺签名密钥 / 双端同密钥 ⇒ 拒绝启动（docs/secur/S-04 §一）。
"""

from __future__ import annotations

import sys
from pathlib import Path

if __package__ in (None, ""):
    # 以脚本方式直接执行时 sys.path 只有脚本所在目录（app/），`app.*` 绝对导入无从解析 ⇒ 补上 server 根。
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import logging
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse, Response
from sqlalchemy import text
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.admin.auth.router import router as admin_auth_router
from app.admin.canteen.router import router as admin_canteen_router
from app.admin.dish.router import router as admin_dish_router
from app.admin.image.router import router as admin_image_router
from app.admin.issue.router import router as admin_issue_router
from app.admin.option.router import router as admin_option_router
from app.admin.stall.router import router as admin_stall_router
from app.core.config import is_production, require_cos_domain, settings
from app.core.db import engine
from app.core.envelope import Envelope
from app.core.errors import (
    CODE_BAD_REQUEST,
    CODE_INTERNAL,
    CODE_NOT_FOUND,
    CODE_RATE_LIMITED,
    ApiError,
)
from app.core.ratelimit import RULE_READ_IP, client_ip, retry_after
from app.student.auth import service as auth_service
from app.student.auth.router import router as auth_router
from app.student.canteen.router import router as canteen_router
from app.student.dish.router import router as dish_router
from app.student.issue.router import router as issue_router

logger = logging.getLogger(__name__)

# 请求体上限：图片 multipart 5MB（docs/secur/S-04）+ 1MB 边界开销预留
MAX_REQUEST_BYTES = 6 * 1024 * 1024


def _cors_origins() -> list[str]:
    """跨域白名单：`*` 或逗号分隔的 Origin（口径见 docs/secur/S-04 §二）。"""
    raw = settings.cors_allow_origins.strip()
    if not raw or raw == "*":
        return ["*"]
    return [origin.strip() for origin in raw.split(",") if origin.strip()]


def _preflight() -> None:
    """启动预检（fail-fast）：凭证缺失 / 配置不一致 ⇒ 生产拒绝启动。"""
    # 图片出参恒绝对 URL，缺 COS_DOMAIN 直接拒绝启动
    require_cos_domain()

    missing = [
        name
        for name, value in (
            ("JWT_SECRET", settings.jwt_secret),
            ("ADMIN_JWT_SECRET", settings.admin_jwt_secret),
        )
        if not value.strip()
    ]
    if missing:
        msg = f"缺少签名密钥 {', '.join(missing)}：无法签发 / 校验凭证"
        if is_production():
            raise RuntimeError(f"{msg} —— 生产须经环境变量注入（docs/secur/S-04 §一）")
        logger.error("%s；dev 模式继续启动，但鉴权端点将一律 401", msg)
    elif settings.jwt_secret == settings.admin_jwt_secret:
        # S-01 红线：双端共用一个 secret ⇒ 学生 token 可直接冒充运营端
        msg = "JWT_SECRET 与 ADMIN_JWT_SECRET 不得相同（学生 token 可冒充运营端，docs/secur/S-01）"
        if is_production():
            raise RuntimeError(msg)
        logger.error(msg)

    if is_production() and _cors_origins() == ["*"]:
        logger.warning(
            "CORS_ALLOW_ORIGINS=*（生产）：任意站点可跨域调用本服务，建议收敛为 Origin 白名单（docs/secur/S-04 §二）"
        )


def _harden[R: Response](response: R) -> R:
    """安全响应头：禁 MIME 嗅探；默认不缓存，端点显式声明的 Cache-Control 优先。"""
    response.headers["X-Content-Type-Options"] = "nosniff"
    if "cache-control" not in response.headers:
        response.headers["Cache-Control"] = "no-store"
    return response


def _envelope_response(code: int, message: str) -> JSONResponse:
    """中间件 / 兜底路径的统一信封（不经过业务异常处理器）。"""
    return _harden(
        JSONResponse(
            status_code=200,
            content=Envelope[None](code=code, message=message).model_dump(),
        )
    )


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    """启动摘要 + DB 连通性探测；关闭时释放微信接口的连接池。"""
    logger.info(
        "启动：APP_ENV=%s 接口文档=%s 跨域=%s",
        settings.app_env or "(缺省=生产)",
        "开" if not is_production() else "关",
        ",".join(_cors_origins()),
    )
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
    except Exception:
        # 只告警不阻断：探活端点须保持可用，便于平台侧观测
        logger.exception("数据库连通性探测失败——数据端点将不可用，请核对 DATABASE_URL 等环境变量")
    yield
    auth_service.close_http_client()


def create_app() -> FastAPI:
    _preflight()

    # 生产不暴露接口文档与契约（docs/secur/S-04 §二）
    docs_enabled = not is_production()

    app = FastAPI(
        title="知行食记",
        version="1.0.0",
        docs_url="/docs" if docs_enabled else None,
        # 契约快照路径与 docs/api/README.md「契约同步」一致
        openapi_url="/openapi.json" if docs_enabled else None,
        lifespan=lifespan,
    )

    # 横切防护（内 → 外）：限频与请求闸门 → 跨域 → 压缩。
    # 后注册者居外层：跨域预检（OPTIONS）最外层即返回、不计入限频；压缩最外层，兜底响应一并压缩。
    @app.middleware("http")
    async def _edge_guards(
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        """限频兜底 + 请求体上限 + 安全响应头（docs/secur/S-03、S-04 §三）。

        中间件不经过异常处理器，命中时直接返回统一信封。
        """
        if request.url.path != "/healthz":
            wait = retry_after(f"global:ip:{client_ip(request)}", RULE_READ_IP)
            if wait:
                return _envelope_response(CODE_RATE_LIMITED, f"操作过于频繁，请 {wait} 秒后再试")

        # 请求体上限：先限频再拒体 ⇒ 超大请求照样消耗配额，无法借大包绕过限频。
        # Content-Length 预检即可（不读体）；分块传输无 CL、无从预判，放行。
        content_length = request.headers.get("content-length", "")
        if content_length.isdigit() and int(content_length) > MAX_REQUEST_BYTES:
            return _envelope_response(CODE_BAD_REQUEST, "请求体过大（上限 6MB）")

        try:
            response = await call_next(request)
        except Exception:
            # 不把内部细节暴露给端上；堆栈连同路径记服务端日志。
            # 在此兜底（而非最外层）：响应仍能获得跨域 / 压缩头。
            logger.exception("未捕获异常：%s %s", request.method, request.url.path)
            return _envelope_response(CODE_INTERNAL, "服务开小差了，请稍后再试")
        return _harden(response)

    # 跨域：运营端在浏览器直连本服务时需要（口径见 docs/secur/S-04 §二）
    app.add_middleware(
        CORSMiddleware,
        allow_origins=_cors_origins(),
        allow_credentials=False,  # 凭证走 Authorization 头，不使用 Cookie
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 压缩：最外层注册；<500B 不压（小信封压了得不偿失）
    app.add_middleware(GZipMiddleware, minimum_size=500)

    @app.exception_handler(ApiError)
    async def _api_error_handler(_: Request, exc: ApiError) -> JSONResponse:
        return JSONResponse(
            status_code=200,
            content=Envelope[None](code=exc.code, message=exc.message).model_dump(),
        )

    @app.exception_handler(RequestValidationError)
    async def _validation_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
        # 只记字段位置与错误类型，不记输入值（口令等敏感值不入日志）
        for err in exc.errors()[:5]:
            logger.warning("参数校验失败：%s %s", err.get("loc"), err.get("type"))
        return JSONResponse(
            status_code=200,
            content=Envelope[None](code=CODE_BAD_REQUEST, message="参数不合法").model_dump(),
        )

    @app.exception_handler(StarletteHTTPException)
    async def _http_exception_handler(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        """未知路径 / 方法等原生 404、405 也归一为信封（「HTTP 恒 200」契约）。"""
        if exc.status_code == 404:
            code, message = CODE_NOT_FOUND, "资源不存在"
        elif exc.status_code == 405:
            code, message = CODE_BAD_REQUEST, "请求方式不支持"
        elif exc.status_code < 500:
            code, message = CODE_BAD_REQUEST, "请求无效"
        else:
            code, message = CODE_INTERNAL, "服务开小差了，请稍后再试"
        return JSONResponse(
            status_code=200,
            content=Envelope[None](code=code, message=message).model_dump(),
        )

    @app.exception_handler(Exception)
    async def _unhandled_handler(request: Request, exc: Exception) -> JSONResponse:
        # 边缘中间件已兜底捕获；此处仅覆盖其外层（压缩 / 跨域中间件自身）的异常
        logger.exception("未捕获异常（中间件层）：%s %s", request.method, request.url.path)
        return JSONResponse(
            status_code=200,
            content=Envelope[None](code=CODE_INTERNAL, message="服务开小差了，请稍后再试").model_dump(),
        )

    # ---- 学生端 ----
    app.include_router(auth_router)
    app.include_router(canteen_router)
    app.include_router(dish_router)
    app.include_router(issue_router)

    # ---- 运营端 ----
    app.include_router(admin_auth_router)
    app.include_router(admin_canteen_router)
    app.include_router(admin_dish_router)
    app.include_router(admin_image_router)
    app.include_router(admin_issue_router)
    app.include_router(admin_option_router)
    app.include_router(admin_stall_router)

    @app.get("/healthz", summary="探活")
    async def healthz() -> dict[str, str]:
        return {"status": "up"}

    return app


app = create_app()


if __name__ == "__main__":
    # 脚本方式直接启动（等价 `uvicorn app.main:app`）：单 worker —— 限频计数在进程内（docs/secur/S-03）。
    import os

    import uvicorn

    uvicorn.run(
        app,
        # 脚本形态面向本地开发：默认只监听回环；容器形态的 0.0.0.0 由 Dockerfile 的 CMD 显式给出
        host=os.environ.get("HOST", "127.0.0.1"),
        # 本地统一 8000（与 client/.env.development、docs 的本地启动命令一致）；容器由 CMD 传 $PORT
        port=int(os.environ.get("PORT", "8000")),
        proxy_headers=True,
        forwarded_allow_ips="*",
    )
