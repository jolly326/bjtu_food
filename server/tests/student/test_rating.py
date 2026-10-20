"""群体风味评级判定（docs/func/client/B2-评分反馈.md）—— 纯函数，不连库。"""

from __future__ import annotations

from decimal import Decimal

import pytest

from app.core.rating import flavor_level


@pytest.mark.parametrize(
    ("avg", "level"),
    [
        (Decimal("5.0"), "S"),
        (Decimal("4.4"), "S"),  # 好评度 85.0
        (Decimal("4.3"), "A"),  # 82.5
        (Decimal("3.8"), "A"),  # 70.0
        (Decimal("3.7"), "B"),  # 67.5
        (Decimal("3.4"), "B"),  # 60.0（60 的浮点临界）
        (Decimal("3.3"), "C"),  # 57.5
        (Decimal("3.0"), "C"),  # 50.0
        (Decimal("2.9"), "D"),  # 47.5
        (Decimal("1.0"), "D"),
    ],
)
def test_level_thresholds(avg: Decimal, level: str) -> None:
    assert flavor_level(avg, 3) == level


def test_below_min_count_is_pending() -> None:
    """投票人数 < 3 ⇒ 待评（None），即使均分很高。"""
    assert flavor_level(Decimal("5.0"), 0) is None
    assert flavor_level(Decimal("5.0"), 2) is None


def test_zero_reaction_is_pending() -> None:
    assert flavor_level(None, 0) is None
    assert flavor_level(None, 5) is None


def test_exactly_three_reactions_rated() -> None:
    """门槛为「≥3 条」：刚好 3 条即出等级。"""
    assert flavor_level(Decimal("5.0"), 3) == "S"
