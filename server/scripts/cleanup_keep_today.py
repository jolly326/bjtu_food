"""一次性数据库清理：保留 2026-11-02 人工导入的数据，删除其余全部。

📌 用途：开发 / 测试库重置用，非生产常驻脚本。
📌 破坏性：删除 dish_issue / dish_rating / 非保留 dish / 非保留 stall / 非保留 canteen。
📌 安全：默认 dry-run（仅打印待删数量，不落地）；加 ``--commit`` 才真正执行，
   全程单事务、失败回滚；``--commit`` 还需键入 ``DELETE-ALL`` 二次确认，防误跑。
📌 保留条件：``KEEP_SINCE`` 及之后（以 ``updated_at`` 为准）视为「今日人工导入」予以保留；
   若当日导入跨越午夜或需按具体 id 保留，请改下方常量（或自行扩展为命令行参数）。

运行（在 ``server/`` 下，且已装依赖、环境变量就绪）：

    python scripts/cleanup_keep_today.py            # 仅 dry-run 打印待删数量
    python scripts/cleanup_keep_today.py --commit   # 真正执行（需二次确认）
"""

from __future__ import annotations

import sys
from datetime import datetime
from pathlib import Path

from sqlalchemy import delete, func, select

# 让脚本可直接 ``python scripts/cleanup_keep_today.py`` 运行（server/ 加入 sys.path）
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.config import settings  # noqa: E402
from app.core.db import SessionLocal  # noqa: E402
from app.models import Canteen, Dish, DishIssue, DishRating, Stall  # noqa: E402

# ⚠️ 保留条件：updated_at >= 该时刻的行视为「今日人工导入」，予以保留。
#    跨午夜 / 按具体 id 保留时，请改此常量（或后续扩展）。
KEEP_SINCE = datetime(2026, 11, 2)


def _collect_keep_ids(session) -> tuple[set[int], set[int], set[int]]:
    """返回 ``(keep_dish_ids, keep_stall_ids, keep_canteen_ids)``。

    保留集合 = 当日导入（``updated_at >= KEEP_SINCE``）∪ 被当日菜品引用的食堂 / 档口。
    后者必须保留：既满足 FK 约束（RESTRICT），也维持业务归属完整。
    """
    keep_dish_ids = {r[0] for r in session.execute(select(Dish.id).where(Dish.updated_at >= KEEP_SINCE))}
    keep_stall_ids = {r[0] for r in session.execute(select(Stall.id).where(Stall.updated_at >= KEEP_SINCE))}
    keep_canteen_ids = {r[0] for r in session.execute(select(Canteen.id).where(Canteen.updated_at >= KEEP_SINCE))}

    if keep_dish_ids:
        refs = session.execute(select(Dish.stall_id, Dish.canteen_id).where(Dish.id.in_(keep_dish_ids)))
        for stall_id, canteen_id in refs:
            if stall_id is not None:
                keep_stall_ids.add(stall_id)
            keep_canteen_ids.add(canteen_id)
    return keep_dish_ids, keep_stall_ids, keep_canteen_ids


def _count(session, model, col, keep_ids: set[int]) -> int:
    stmt = select(func.count()).select_from(model)
    if keep_ids:
        stmt = stmt.where(col.notin_(keep_ids))
    return session.scalar(stmt) or 0


def _delete(session, model, col, keep_ids: set[int]) -> int:
    stmt = delete(model)
    if keep_ids:
        stmt = stmt.where(col.notin_(keep_ids))
    return session.execute(stmt).rowcount


def _report(session, *, committing: bool) -> tuple[set[int], set[int], set[int]]:
    keep_dish_ids, keep_stall_ids, keep_canteen_ids = _collect_keep_ids(session)

    dish_total = session.scalar(select(func.count()).select_from(Dish)) or 0
    issue_total = session.scalar(select(func.count()).select_from(DishIssue)) or 0
    rating_total = session.scalar(select(func.count()).select_from(DishRating)) or 0
    stall_total = session.scalar(select(func.count()).select_from(Stall)) or 0
    canteen_total = session.scalar(select(func.count()).select_from(Canteen)) or 0

    issue_del = _count(session, DishIssue, DishIssue.dish_id, keep_dish_ids)
    rating_del = _count(session, DishRating, DishRating.dish_id, keep_dish_ids)
    dish_del = _count(session, Dish, Dish.id, keep_dish_ids)
    stall_del = _count(session, Stall, Stall.id, keep_stall_ids)
    canteen_del = _count(session, Canteen, Canteen.id, keep_canteen_ids)

    mode = "【执行】" if committing else "【DRY-RUN】"
    print(f"\n{mode} 保留条件 KEEP_SINCE = {KEEP_SINCE.isoformat()}")
    print("────────── 各表现状 / 保留 / 待删 ──────────")
    print(f"  dish        : 总 {dish_total:>5}  保留 {len(keep_dish_ids):>5}  待删 {dish_del:>5}")
    print(f"  dish_issue  : 总 {issue_total:>5}  保留 {issue_total - issue_del:>5}  待删 {issue_del:>5}")
    print(f"  dish_rating : 总 {rating_total:>5}  保留 {rating_total - rating_del:>5}  待删 {rating_del:>5}")
    print(f"  stall       : 总 {stall_total:>5}  保留 {len(keep_stall_ids):>5}  待删 {stall_del:>5}")
    print(f"  canteen     : 总 {canteen_total:>5}  保留 {len(keep_canteen_ids):>5}  待删 {canteen_del:>5}")
    print("──────────────────────────────────────────")
    return keep_dish_ids, keep_stall_ids, keep_canteen_ids


def main() -> None:
    committing = "--commit" in sys.argv[1:]
    session = SessionLocal()
    try:
        keep_dish_ids, keep_stall_ids, keep_canteen_ids = _report(session, committing=committing)

        if not committing:
            print("未加 --commit：以上仅为 dry-run，未做任何修改。")
            return

        confirm = input('确认删除请键入 "DELETE-ALL"（其他任意输入取消）: ')
        if confirm.strip() != "DELETE-ALL":
            print("已取消，未做任何修改。")
            return

        # 先子后父：纠错/点赞 → 菜品 → 档口 → 食堂（食堂最后，避免 RESTRICT 拦截）
        n_issue = _delete(session, DishIssue, DishIssue.dish_id, keep_dish_ids)
        n_rating = _delete(session, DishRating, DishRating.dish_id, keep_dish_ids)
        n_dish = _delete(session, Dish, Dish.id, keep_dish_ids)
        n_stall = _delete(session, Stall, Stall.id, keep_stall_ids)
        n_canteen = _delete(session, Canteen, Canteen.id, keep_canteen_ids)
        session.commit()
        print(
            f"已提交：删除 dish_issue={n_issue} dish_rating={n_rating} "
            f"dish={n_dish} stall={n_stall} canteen={n_canteen}"
        )
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


if __name__ == "__main__":
    main()
