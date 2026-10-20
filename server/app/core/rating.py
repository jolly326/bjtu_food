"""群体风味评级判定（docs/func/client/B2-评分反馈.md、docs/schema/dish.md）。

口径：
* 好评度 =（avg_reaction − 1）× 25（avg_reaction 为 `5`/`3`/`1` 三态均值，1 位小数）
* 投票人数 < 3 ⇒ **待评**（``None``，端上渲染「风味评级 / 待评」）
* 五档：S ≥ 85（臻荐）｜A 70~<85（极荐）｜B 60~<70（推荐）｜C 50~<60（尚可）｜D < 50（平实）
* 🔴 只用中性口碑词，不含劝退 / 负面表述；评级是口味口碑汇总，不是菜品质量评价

🔴 评级不落库 —— 由 ``dish.avg_reaction`` + ``dish.reaction_count`` 实时判定。

横切位置说明：本函数是**无状态纯函数**，被学生端多个域（dish 卡片、record 食谱）
共用，故置于 ``core`` 横切层；调用方仍是 service 层，分层链路不变。
"""

from __future__ import annotations

from decimal import Decimal

MIN_REACTION_COUNT = 3

# 档位从高到低；命中即返回
THRESHOLDS: tuple[tuple[str, Decimal], ...] = (
    ("S", Decimal("85")),
    ("A", Decimal("70")),
    ("B", Decimal("60")),
    ("C", Decimal("50")),
)


def flavor_level(avg_reaction: Decimal | float | None, reaction_count: int | None) -> str | None:
    """按缓存列判定群体风味评级；样本不足 / 零评分 ⇒ ``None``（待评）。"""
    if avg_reaction is None or not reaction_count or reaction_count < MIN_REACTION_COUNT:
        return None
    # 存档值恒为 1 位小数，用 Decimal 运算避免 3.4*25 = 59.999… 的二进制浮点误差
    score = (Decimal(str(avg_reaction)) - Decimal("1")) * Decimal("25")
    for level, floor in THRESHOLDS:
        if score >= floor:
            return level
    return "D"
