/**
 * 日期格式化（页头日期 + 记忆时间线共用，避免各页重复实现）。
 */

/** 页头日期（Julienne 顶部式，星期在前）：`星期日，10月18日`（美味 / 记忆页头共用） */
export function todayLabelWeekdayCN(): string {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `星期${week}，${d.getMonth() + 1}月${d.getDate()}日`
}

/** `YYYY-MM-DD` → `MM.DD`（零填充，如 `10.18` / `05.20`） */
export function formatDayLabel(day: string): string {
  return `${day.slice(5, 7)}.${day.slice(8, 10)}`
}
