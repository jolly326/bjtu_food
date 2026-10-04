/**
 * 菜品 / 评分的**展示派生值**（纯函数，无副作用）—— 全站统一口径。
 *
 * 本文件收口全站菜品 / 评分展示派生值（joinLocation / hasDiscount / formatRating）单一口径，避免各页重复实现导致漂移：
 * · `joinLocation` —— 位置副信息拼接（空段自动跳过；find 结果 / 详情页 / 反馈锚定卡复用）
 * · `hasDiscount`  —— 「有折扣」唯一判据
 * · `formatRating` —— 评分展示口径（恒一位小数）
 *
 * ⚠️ 明确**未合并**的近似逻辑（语义不同，不可误并）：
 * · 菜品详情「零评价」时展示**文案**「暂无评分」（不是数字 `0.0`）—— 该分支由消费方按
 *   `avgRating == null` 判定，不并入 `formatRating`（它恒出数字）。
 */

/** 位置副信息「食堂 · 楼层 · 档口」：空段自动跳过；全空时返回空串（兜底文案由调用方决定） */
export function joinLocation(...segments: Array<string | null | undefined>): string {
  return segments.filter((s): s is string => !!s).join(' · ')
}

/** 「有折扣」唯一判据：`originalPrice` 有值且严格大于 `price`（不引入第三个价格字段） */
export function hasDiscount(price?: number | null, originalPrice?: number | null): boolean {
  return originalPrice != null && price != null && originalPrice > price
}

/**
 * 评分文案：恒保留一位小数（避免同一分值在不同页显示为 `4` / `4.5`）。
 * `0` / `null` / `undefined` → `0.0`；「无评分显示 `-`」是另一种语义，由调用方自行判断。
 */
export function formatRating(rating?: number | null): string {
  return Number(rating || 0).toFixed(1)
}
