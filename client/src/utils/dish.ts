/**
 * 菜品 / 评分的**展示派生值**（纯函数，无副作用）—— 全站统一口径
 *
 * 背景（UI 统一 Loop Round 17 · 代码质量轮）：以下判据与格式原先在各页各写一份，
 * 口径分散、易随改动漂移；现集中到本文件统一维护：
 * · `joinLocation` —— 「食堂 · 档口」副信息（find 结果 / 反馈选品 / 反馈改单，三处逐字重复）
 * · `hasDiscount`  —— 「有折扣」唯一判据（find 结果 / 菜品信息卡）
 * · `formatRating` —— 评分展示口径（恒一位小数）
 *
 * ⚠️ 明确**未合并**的近似逻辑（语义不同，不可误并）：
 * · `useDishPage.locationText`：详情页「食堂 · 楼层 · 档口」三段 + 「未知位置」兜底 —— 段数、顺序与兜底文案都不同；
 * · 菜品详情「零评价」时展示的是**文案**「暂无评分」（不是数字 `0.0`）—— 该分支由消费方按 `ratingCount === 0`
 *   判定，不并入 `formatRating`（它恒出数字）。
 */

/** 「食堂名 · 档口名」副信息：空段自动跳过；两段皆空时返回空串（调用方按需决定是否隐藏该行） */
export function joinLocation(canteen?: string | null, stallName?: string | null): string {
  return [canteen, stallName].filter(Boolean).join(' · ')
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
