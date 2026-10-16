"""poster 域业务规则（docs/func/client/A1-档口海报.md）。"""

from __future__ import annotations

import random

from sqlalchemy.orm import Session

from app.student.poster import repo


def list_posters(session: Session) -> list:
    """已上传海报的档口，按 id 取出后**随机重掷**。

    随机在应用层做（而非 SQL RAND()）—— 候选集受 status 无关约束（档口无状态列），
    仅过滤 poster 非空；量级小，打散代价可忽略。
    """
    rows = repo.list_with_poster(session)
    random.shuffle(rows)
    return rows
