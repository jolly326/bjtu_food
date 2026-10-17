"""FastAPI 应用入口。

统一异常处理：把业务异常 / 参数校验异常 / 未捕获异常都翻译成
``{code, message, data}`` 信封（HTTP 状态恒 200，业务码在 body 里）。
"""

from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.admin.auth.router import router as admin_auth_router
from app.admin.canteen.router import router as admin_canteen_router
from app.admin.dish.router import router as admin_dish_router
from app.admin.image.router import router as admin_image_router
from app.admin.issue.router import router as admin_issue_router
from app.admin.option.router import router as admin_option_router
from app.admin.poster.router import router as admin_poster_router
from app.admin.record.router import router as admin_record_router
from app.admin.stall.router import router as admin_stall_router
from app.core.config import settings
from app.core.envelope import Envelope
from app.core.errors import CODE_BAD_REQUEST, CODE_INTERNAL, ApiError
from app.student.auth.router import router as auth_router
from app.student.canteen.router import router as canteen_router
from app.student.dish.router import router as dish_router
from app.student.issue.router import router as issue_router
from app.student.poster.router import router as poster_router
from app.student.record.router import router as record_router


def _cors_origins() -> list[str]:
    """跨域白名单：`*` 或逗号分隔的 Origin（口径见 docs/secur/S-04 §二）。"""
    raw = settings.cors_allow_origins.strip()
    if not raw or raw == "*":
        return ["*"]
    return [origin.strip() for origin in raw.split(",") if origin.strip()]


def create_app() -> FastAPI:
    app = FastAPI(
        title="知行食记",
        version="1.0.0",
        docs_url="/docs",
        # 契约快照路径与 docs/api/README.md「契约同步」一致
        openapi_url="/openapi.json",
    )

    # 跨域：运营端在浏览器直连本服务时需要（口径见 docs/secur/S-04 §二）
    app.add_middleware(
        CORSMiddleware,
        allow_origins=_cors_origins(),
        allow_credentials=False,  # 凭证走 Authorization 头，不使用 Cookie
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.exception_handler(ApiError)
    async def _api_error_handler(_: Request, exc: ApiError) -> JSONResponse:
        return JSONResponse(
            status_code=200,
            content=Envelope[None](code=exc.code, message=exc.message).model_dump(),
        )

    @app.exception_handler(RequestValidationError)
    async def _validation_handler(_: Request, __: RequestValidationError) -> JSONResponse:
        return JSONResponse(
            status_code=200,
            content=Envelope[None](code=CODE_BAD_REQUEST, message="参数不合法").model_dump(),
        )

    @app.exception_handler(Exception)
    async def _unhandled_handler(_: Request, __: Exception) -> JSONResponse:
        # 不把内部细节暴露给端上；细节记服务端日志
        return JSONResponse(
            status_code=200,
            content=Envelope[None](code=CODE_INTERNAL, message="服务开小差了，请稍后再试").model_dump(),
        )

    # ---- 学生端 ----
    app.include_router(auth_router)
    app.include_router(canteen_router)
    app.include_router(dish_router)
    app.include_router(poster_router)
    app.include_router(record_router)
    app.include_router(issue_router)

    # ---- 运营端 ----
    app.include_router(admin_auth_router)
    app.include_router(admin_canteen_router)
    app.include_router(admin_dish_router)
    app.include_router(admin_image_router)
    app.include_router(admin_issue_router)
    app.include_router(admin_option_router)
    app.include_router(admin_poster_router)
    app.include_router(admin_record_router)
    app.include_router(admin_stall_router)

    @app.get("/healthz", summary="探活")
    async def healthz() -> dict[str, str]:
        return {"status": "up"}

    return app


app = create_app()
