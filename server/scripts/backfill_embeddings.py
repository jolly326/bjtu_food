"""存量菜品向量回填：切换 ``EMBEDDING_PROVIDER`` / ``EMBEDDING_MODEL`` 后跑一次。

用法（在 ``server/`` 下执行）::

    python scripts/backfill_embeddings.py --dry-run      # 只统计
    python scripts/backfill_embeddings.py --limit 50     # 先小批量试
    python scripts/backfill_embeddings.py                # 全量

向量落在 ``dish_embedding``（``(dish_id, model)`` 联合主键，可并存多模型）；
回填期间检索端只信任与当前模型一致的行（口径见 docs/schema/dish_embedding.md），建议低峰执行。

🔴 执行前确认向量服务配置已就绪（``EMBEDDING_*``）。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# 允许 ``python scripts/backfill_embeddings.py`` 直接运行（把 server/ 放进 sys.path）
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from sqlalchemy import select

from app.core.db import session_scope
from app.core.runtime.cache import invalidate
from app.core.search.embedding import current_model, embed
from app.models import Dish, DishEmbedding

_BATCH = 20


def main() -> int:
    parser = argparse.ArgumentParser(description="回填菜品语义向量")
    parser.add_argument("--dry-run", action="store_true", help="只统计，不写库")
    parser.add_argument("--limit", type=int, default=0, help="最多处理多少行（0 = 全部）")
    parser.add_argument(
        "--only-missing",
        action="store_true",
        help="只处理当前模型尚无向量的菜品",
    )
    args = parser.parse_args()

    model = current_model()
    print(f"当前向量模型：{model}（dry-run={args.dry_run}）")

    done = 0
    skipped = 0
    with session_scope() as session:
        query = select(Dish).order_by(Dish.id)
        if args.limit > 0:
            query = query.limit(args.limit)
        for dish in session.scalars(query):
            text = (dish.description or dish.name or "").strip()
            if not text:
                skipped += 1
                continue
            row = session.get(DishEmbedding, (dish.id, model))
            if args.only_missing and row is not None:
                skipped += 1
                continue
            if not args.dry_run:
                vector = embed(text)
                if row is None:
                    session.add(DishEmbedding(dish_id=dish.id, model=model, vector=vector))
                else:
                    row.vector = vector
            done += 1
            if not args.dry_run and done % _BATCH == 0:
                session.flush()
                print(f"  已处理 {done} 行…")

    if not args.dry_run:
        invalidate("student:dishes")
    print(f"完成：更新 {done} 行，跳过 {skipped} 行")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
