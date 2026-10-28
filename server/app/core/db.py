"""数据库引擎与会话管理。

时间戳纪律：``created_at`` / ``updated_at`` 一律由 MySQL 写入
（列默认值 + ``ON UPDATE``），应用层**不赋值**这两列。
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

from fastapi import Request
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import settings

engine = create_engine(
    settings.sqlalchemy_url,
    pool_pre_ping=True,
    pool_recycle=3600,
    # 单 worker 显式池参数：稳态 10 + 突发 10 —— 须与容器 CPU 配额 / MySQL max_connections 匹配
    pool_size=10,
    max_overflow=10,
    pool_timeout=30,  # 等连接超时快速失败，避免请求悬挂
    future=True,
    echo=False,
)

SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)


def get_session(request: Request) -> Iterator[Session]:
    """FastAPI 依赖：请求级会话——写请求提交，读请求不提交。

    写端点（POST / PUT / PATCH / DELETE）依赖本语义落库；GET 为只读，
    跳过空事务提交（``close()`` 会回滚未提交的只读事务）。
    """
    session = SessionLocal()
    try:
        yield session
        if request.method != "GET":
            session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


@contextmanager
def session_scope() -> Iterator[Session]:
    """写路径：进入即开事务，正常结束提交，异常回滚。"""
    session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
