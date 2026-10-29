"""应用配置。

环境文件按运行档位选择（路径锚定 ``server/``，与启动时的工作目录无关）：

* ``development``（缺省）⇒ ``server/.env.development`` + ``server/.env.local``（后者覆盖前者）
* ``production``         ⇒ ``server/.env.production``

档位由 ``APP_PROFILE`` 决定；进程环境变量优先级**最高** —— 生产容器不携带任何
``.env*`` 文件，凭据一律由云托管控制台注入。

数据库连接接受两组等价变量名，任填一套即可：

* ``DATABASE_URL`` / ``DATABASE_USER`` / ``DATABASE_PASSWORD``
* ``SPRING_DATASOURCE_URL`` / ``SPRING_DATASOURCE_USERNAME`` / ``SPRING_DATASOURCE_PASSWORD``

都填时以第一组为准。
"""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from urllib.parse import quote

from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_DB_NAME = "bjtu_food"

# 环境文件锚定到 server/，与启动时的工作目录无关
_SERVER_DIR = Path(__file__).resolve().parents[2]

# APP_PROFILE 视为生产档位的取值
_PRODUCTION_PROFILES = {"prod", "production"}


def env_files() -> tuple[str, ...]:
    """按 ``APP_PROFILE`` 返回要加载的环境文件（缺省 = 开发档位）。

    文件不存在时由 pydantic-settings 静默跳过 —— 生产镜像不含任何 ``.env*``，
    因此生产实际只吃进程环境变量。
    """
    profile = (os.getenv("APP_PROFILE") or "development").strip().lower()
    if profile in _PRODUCTION_PROFILES:
        return (str(_SERVER_DIR / ".env.production"),)
    return (
        str(_SERVER_DIR / ".env.development"),
        str(_SERVER_DIR / ".env.local"),
    )


class Settings(BaseSettings):
    """环境变量 -> 配置对象（字段名即环境变量名，大小写不敏感）。"""

    model_config = SettingsConfigDict(
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ---- 运行模式：prod（缺省）/ dev ----
    # 🔴 生产不暴露 /docs 与 /openapi.json（口径见 docs/secur/S-04 §二）
    app_env: str = "prod"

    # ---- 数据库（第一组等价变量名）----
    database_url: str = ""
    database_user: str = ""
    database_password: str = ""

    # ---- 数据库（第二组等价变量名）----
    spring_datasource_url: str = ""
    spring_datasource_username: str = ""
    spring_datasource_password: str = ""

    # ---- 凭证 ----
    jwt_secret: str = ""
    # 学生端 / 运营端 JWT 共用有效期（默认 7 天）
    jwt_ttl_seconds: int = 7 * 24 * 3600
    admin_jwt_secret: str = ""

    # ---- 跨域（运营端浏览器直连时需要）----
    # `*` 或逗号分隔的 Origin 白名单（口径见 docs/secur/S-04 §二）
    cors_allow_origins: str = "*"

    # ---- 微信 ----
    wechat_appid: str = ""
    wechat_secret: str = ""

    # ---- 腾讯云 COS ----
    cos_bucket: str = ""
    cos_region: str = ""
    cos_secret_id: str = ""
    cos_secret_key: str = ""
    # 图片访问域名（如 https://xxx-1250000000.cos.ap-guangzhou.myqcloud.com）
    # 🔴 必填：出参图片一律绝对 URL，缺失时启动即拒绝（见 require_cos_domain）
    cos_domain: str = ""

    @property
    def sqlalchemy_url(self) -> str:
        """返回 SQLAlchemy 连接串。

        优先用 ``DATABASE_URL``；否则用另一组变量名的 host:port 拼出连接串。
        该 URL 里可能不带库名（形如 ``host:port``），此时补默认库名。
        """
        if self.database_url:
            return self.database_url

        host_port = (self.spring_datasource_url or "").strip()
        # 容忍 jdbc:mysql://host:port/db 形式
        if "://" in host_port:
            host_port = host_port.split("://", 1)[1]
        host_port = host_port.split("?", 1)[0].rstrip("/")
        if not host_port:
            host_port = "127.0.0.1:3306"

        # 取最后一段作为库名（若 URL 里带了 db）
        db_name = DEFAULT_DB_NAME
        if "/" in host_port:
            host_port, _, maybe_db = host_port.rpartition("/")
            if maybe_db:
                db_name = maybe_db

        user = self.database_user or self.spring_datasource_username or "root"
        password = self.database_password or self.spring_datasource_password or ""
        return f"mysql+pymysql://{quote(user)}:{quote(password)}@{host_port}/{db_name}?charset=utf8mb4"


@lru_cache
def get_settings() -> Settings:
    return Settings(_env_file=env_files())


settings = get_settings()


def is_production() -> bool:
    """是否生产模式：``APP_ENV`` 取 dev / development / local 之外的值（含缺省）均视为生产。"""
    return settings.app_env.strip().lower() not in {"dev", "development", "local"}


def require_cos_domain() -> str:
    """取图片访问域名；未配置直接拒绝启动。

    🔴 出参图片契约是**绝对 URL**（docs/schema/dish.md），
    因此 ``COS_DOMAIN`` 是必填项：宁可启动失败，也不产出相对路径。
    """
    domain = settings.cos_domain.strip().rstrip("/")
    if not domain:
        raise RuntimeError("COS_DOMAIN 未配置：图片出参必须是绝对 URL，请配置访问域名后启动")
    return domain
