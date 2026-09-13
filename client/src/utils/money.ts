/**
 * 金额换算工具（分 ↔ 元，统一在 api 层处理）
 *
 * 规约（docs/web-ui.md）：金额分↔元换算必须统一走本模块，
 * 页面 / 模板禁止直接 /100 或 *100 裸算。
 *   - 后端返回的金额单位为「分」（整数）
 *   - 前端用户输入的金额单位为「元」（小数）
 */
export function fenToYuan(fen: number | null | undefined): number {
  if (fen == null || Number.isNaN(fen)) return 0
  return Math.round(fen) / 100
}

export function yuanToFen(yuan: number | null | undefined): number {
  if (yuan == null || Number.isNaN(yuan)) return 0
  return Math.round(yuan * 100)
}

/**
 * 统一价格展示格式：入参为「元」，输出固定两位小数字符串（如 12.5 → "12.50"）。
 * 入参口径说明：页面/组件拿到的价格均已由 API 层 fenToYuan 换算为元（§3.x 金额红线：
 * 页面不换算金额），故本函数入参取元而非分，仅统一展示格式（补零到两位）；
 * 内部经「元→分→元」整数化消除二进制浮点尾差后再 toFixed(2)。
 * 全站价格展示唯一口径：DishCard / DishInfoCard / FindResults 均引用此函数，替代各自裸插值/toFixed(2)。
 */
export function formatPrice(yuan: number | null | undefined): string {
  return fenToYuan(yuanToFen(yuan)).toFixed(2)
}
