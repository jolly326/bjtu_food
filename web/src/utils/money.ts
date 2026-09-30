/**
 * 金额工具（权威源：docs/web/feature/README.md「金额约定」）。
 * 接口一律以「分」传输；分↔元换算只允许在此处，禁止页面/组件裸算。
 */

/** 分 → 元（展示用）。null/undefined 视为 0。 */
export function fenToYuan(fen: number | null | undefined): number {
  if (fen == null) return 0
  return Math.round(fen) / 100
}

/** 元 → 分（提交用）。字符串自动 parseFloat；非法 → 0。 */
export function yuanToFen(yuan: number | string): number {
  const n = typeof yuan === 'string' ? parseFloat(yuan) : yuan
  if (!isFinite(n)) return 0
  return Math.round(n * 100)
}

/** 格式化展示：¥12.34（始终两位小数）。 */
export function formatYuan(fen: number | null | undefined): string {
  return `¥${fenToYuan(fen).toFixed(2)}`
}
