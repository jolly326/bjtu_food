/**
 * 日期格式化（页头日期 + 记忆时间线共用，避免各页重复实现）。
 *
 * 页头日期在页面生命周期内恒定，故由各页**调用一次后存为常量**（不做每次渲染重算），
 * 本模块只负责格式本身。
 */

/** 星期中文单字：按 `Date#getDay()` 下标取值（0 = 周日） */
const WEEK_CN = ['日', '一', '二', '三', '四', '五', '六'] as const

/** 页头日期（Julienne 顶部式，星期在前）：`星期日，10月18日`（美味 / 记忆页头共用） */
export function todayLabelWeekdayCN(): string {
  const d = new Date()
  return `星期${WEEK_CN[d.getDay()]}，${d.getMonth() + 1}月${d.getDate()}日`
}

/** `YYYY-MM-DD` → `MM.DD`（零填充，如 `10.18` / `05.20`）—— 记忆页日期分组标记 */
export function formatDayLabel(day: string): string {
  return `${day.slice(5, 7)}.${day.slice(8, 10)}`
}
