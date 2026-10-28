import type { Page } from '@/api/types'

/**
 * 选择器「取全量」：逐页拉取直到末页（服务端单页上限 50），
 * 避免用 `pageSize=50` 冒充全量导致下拉静默漏项。
 *
 * `maxPages` 为兜底上限，防止异常分页导致死循环。
 */
export async function fetchAllPages<T>(
  fetchPage: (page: number, pageSize: number) => Promise<Page<T>>,
  pageSize = 50,
  maxPages = 20,
): Promise<T[]> {
  const out: T[] = []
  for (let page = 1; page <= maxPages; page++) {
    const res = await fetchPage(page, pageSize)
    out.push(...res.records)
    if (res.records.length < pageSize) return out
  }
  if (import.meta.env.DEV) {
    console.warn(`[useOptions] 选择器超过 ${maxPages} 页上限，已截断（pageSize=${pageSize}）`)
  }
  return out
}
