/**
 * 日期 / 时间格式化（页头日期 + 记忆时间线共用，避免各页重复实现）。
 */

/** 页头日期（zh-CN） */
export function todayLabel(): string {
  return new Date().toLocaleDateString('zh-CN')
}

/** `YYYY-MM-DD` → `M月D日` */
export function formatDayLabel(day: string): string {
  return `${Number(day.slice(5, 7))}月${Number(day.slice(8, 10))}日`
}

/** ISO datetime → `HH:mm` */
export function formatClock(value: string): string {
  return value.slice(11, 16)
}
