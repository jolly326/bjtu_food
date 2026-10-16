/**
 * 金额转换（单位：分 <-> 元）。
 *
 * 口径（docs/api/README.md「金额」）：接口一律以「分」传输；
 * 分 <-> 元转换**只允许在这一处**进行，页面 / 组件内不得裸算。
 */

/** 分 -> 元（展示用，固定两位小数） */
export function fenToYuan(fen: number): string {
  return (fen / 100).toFixed(2)
}

/** 元 -> 分（提交用）；非法输入返回 NaN，由调用方拦截 */
export function yuanToFen(yuan: string | number): number {
  if (typeof yuan === 'string' && yuan.trim() === '') return Number.NaN
  const n = typeof yuan === 'number' ? yuan : Number(yuan)
  if (!Number.isFinite(n) || n < 0) return Number.NaN
  return Math.round(n * 100)
}
