"""学生端「美味」tab：推荐菜品卡片。

端点（docs/api/client/dishes.md）：
    GET /dishes?canteenId=&pageSize=   🔑 需登录
    - 只返在售（status='on'）
    - 顺序 = 加权随机（权重取 avg_reaction；零评分按基线权重）
    - wanted = 当前用户是否已有该菜的**未评分**记录
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.core.deps import current_student_id
from app.core.envelope import Envelope, PageResult, page_ok
from app.core.pagination import normalize_page
from app.core.ratelimit import RULE_READ_ACCOUNT, check
from app.student.dish import service
from app.student.dish.service import DishCardOut

router = APIRouter(tags=["dish"])


@router.get("/dishes", response_model=Envelope[PageResult[DishCardOut]], summary="推荐菜品卡片")
def list_dishes(
    canteen_id: int | None = Query(default=None, alias="canteenId"),
    page_size: int | None = Query(default=None, alias="pageSize"),
    student_id: int = Depends(current_student_id),
    session: Session = Depends(get_session),
) -> Envelope[PageResult[DishCardOut]]:
    check(f"read:u:{student_id}", RULE_READ_ACCOUNT)
    # 随机重掷与翻页不兼容（每次请求重新洗牌），故只取 size 条
    limit = normalize_page(None, page_size, default_size=20).size
    cards = service.sample_cards(session, student_id=student_id, canteen_id=canteen_id, limit=limit)
    return page_ok(cards)
