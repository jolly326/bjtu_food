"""应用配置。

变量真源是 ``server/.env``（已 gitignore）。这里同时兼容两套变量名：

* 新设计：``DATABASE_URL`` / ``DATABASE_USER`` / ``DATABASE_PASSWORD``
* 旧体系：``SPRING_DATASOURCE_URL`` / ``SPRING_DATASOURCE_USERNAME`` / ``SPRING_DATASOURCE_PASSWORD``

两套任填其一即可；都填时以新设计为准。
"""

from __future__ import annotations

from functools import lru_cache
from urllib.parse import quote

from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_DB_NAME = "bjtu_food"


class Settings(BaseSettings):
    """环境变量 -> 配置对象（字段名即环境变量名，大小写不敏感）。"""

    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ---- 数据库（新设计变量名）----
    database_url: str = ""
    database_user: str = ""
    database_password: str = ""

    # ---- 数据库（兼容旧变量名）----
    spring_datasource_url: str = ""
    spring_datasource_username: str = ""
    spring_datasource_password: str = ""

    # ---- 凭证 ----
    jwt_secret: str = ""
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
    # 未配置时路径工具原样返回相对路径（仅开发期）
    cos_domain: str = ""

    @property
    def sqlalchemy_url(self) -> str:
        """返回 SQLAlchemy 连接串。

        优先用 ``DATABASE_URL``；否则用旧变量名拼出 ``host:port/db``。
        旧 URL 里可能不带库名（形如 ``host:port``），此时补默认库名。
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
    return Settings()


settings = get_settings()
