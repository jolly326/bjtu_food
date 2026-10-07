/**
 * 日期时间展示格式化 —— **管理端全站唯一入口**。
 *
 * <p>口径（见 [列表页模板 §一](../../../docs/ui/web/列表页模板.md)）：页面**一律**把时间值交给本函数，
 * **SHALL NOT** 直接把后端字符串渲染到模板里。原因：后端契约的时间格式是
 * `yyyy-MM-dd HH:mm:ss`（见 [api/README §时间](../../../docs/api/README.md)），
 * 但任何一处漏改 / 历史产物 / 代理改写都可能让 ISO-8601 形态（`2026-10-05T12:09:17`）直出，
 * 表格里出现 `T` 分隔符。本函数做**幂等归一**：目标形态原样（截断到秒）、
 * ISO 形态去掉 `T` 与毫秒 / 时区尾巴，其余可解析形态按本地时区补齐。
 */
const PAD = (n: number): string => String(n).padStart(2, '0')

/**
 * 格式化为 `YYYY-MM-DD HH:mm:ss`。
 *
 * @param value       时间字符串（后端 `yyyy-MM-dd HH:mm:ss` / ISO-8601 / 其它可被 `Date` 解析的形态）
 * @param placeholder 空值（`null` / `undefined` / 空串）与不可解析时的回落文案（默认 `—`）
 */
export function formatDateTime(value?: string | null, placeholder = '—'): string {
  if (value == null || value === '') return placeholder
  const raw = value.trim()
  if (raw === '') return placeholder

  // ① 已是「日期 时间」或 ISO 形态：取到秒为止，丢弃毫秒与时区尾巴（`T` 一并归一为空格）
  const matched = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/.exec(raw)
  if (matched) return `${matched[1]} ${matched[2]}`

  // ② 其余形态（如 `2026/10/05 12:09`）：交给 `Date` 解析后按本地时区补齐到秒
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) return placeholder
  return (
    `${parsed.getFullYear()}-${PAD(parsed.getMonth() + 1)}-${PAD(parsed.getDate())}` +
    ` ${PAD(parsed.getHours())}:${PAD(parsed.getMinutes())}:${PAD(parsed.getSeconds())}`
  )
}
