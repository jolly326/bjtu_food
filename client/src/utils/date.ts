/**
 * 日期 / 时间格式化（页头日期 + 记忆时间线共用，避免各页重复实现）。
 */

/** 页头日期（zh-CN，默认格式：`2026年10月18日`） */
export function todayLabel(): string {
  return new Date().toLocaleDateString('zh-CN')
}

/** 页头日期（Julienne 风格）：`10月18日 星期六`（美味页页头用） */
export function todayLabelCN(): string {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 星期${week}`
}

/** 页头日期（数字斜线风格）：`2026/10/18`（零填充，记忆页页头用） */
export function todayLabelNumeric(): string {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}/${mm}/${dd}`
}

/** `YYYY-MM-DD` → `MM.DD`（零填充，如 `10.18` / `05.20`） */
export function formatDayLabel(day: string): string {
  return `${day.slice(5, 7)}.${day.slice(8, 10)}`
}

/** ISO datetime → `HH:mm` */
export function formatClock(value: string): string {
  return value.slice(11, 16)
}
