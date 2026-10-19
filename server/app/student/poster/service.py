"""poster 域业务规则（docs/func/client/A1-风物.md）。"""

from __future__ import annotations

from sqlalchemy.orm import Session

from app.models import Poster
from app.student.poster import repo


def list_posters(session: Session) -> list[Poster]:
    """上架海报，按运营手排的**固定序**下发（sort 升序；不随机、不重掷）。"""
    return repo.list_on_shelf(session)
