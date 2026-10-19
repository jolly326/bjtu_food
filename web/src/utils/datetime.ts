/**
 * 日期时间展示（服务端 ISO 字符串 → 界面文案）。
 *
 * 口径：列表指标行 / 概览统一「YYYY-MM-DD HH:mm」；
 * 仅做展示转换，不做时区换算（服务端已按本地时区吐出）。
 */

/** ISO 字符串 → `YYYY-MM-DD HH:mm`；空值 / 非法值返回 `—` */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
