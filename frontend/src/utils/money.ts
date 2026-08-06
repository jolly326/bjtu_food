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
