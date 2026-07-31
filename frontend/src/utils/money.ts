/**
 * 金额单位换算工具
 *
 * 后端一律以「分」存储与传输（int），前端展示层负责 /100 转「元」，
 * 前端提交时转回「分」。集中在此处避免各业务模块重复实现。
 */

/** 分 → 元（四舍五入保留两位小数） */
export function fenToYuan(fen?: number | null): number {
  if (fen == null) return 0
  return Math.round((fen / 100) * 100) / 100
}

/** 元 → 分（向上取整，避免浮点丢精度；12.005 元 → 1201 分） */
export function yuanToFen(yuan?: number | null): number {
  if (yuan == null) return 0
  return Math.round(yuan * 100)
}

/** 分 → 元字符串（带 ¥ 前缀，用于展示） */
export function formatYuan(fen?: number | null): string {
  return `¥${fenToYuan(fen).toFixed(2)}`
}
