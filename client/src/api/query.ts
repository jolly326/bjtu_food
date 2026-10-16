/** 查询串构造（仅拼接非空参数）。 */

export function toQuery(params?: Record<string, string | number | undefined>): string {
  if (!params) return ''
  const pairs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}
