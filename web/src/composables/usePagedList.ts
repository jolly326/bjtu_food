import { ref, shallowRef } from 'vue'

/**
 * 分页加载组合式（按 docs/web/feature 分页约定：结束判据 = 本页条数 < pageSize）。
 * fetcher 返回当前页 records 数组。
 */
export function usePagedList<T>(
  fetcher: (page: number, pageSize: number) => Promise<T[]>,
  pageSize = 20,
) {
  const items = shallowRef<T[]>([])
  const page = ref(1)
  const loading = ref(false)
  const finished = ref(false)
  const error = ref<string | null>(null)

  async function load(reset = false): Promise<void> {
    if (loading.value) return
    if (reset) {
      page.value = 1
      finished.value = false
      items.value = []
    }
    loading.value = true
    error.value = null
    try {
      const rows = await fetcher(page.value, pageSize)
      if (reset) items.value = [...rows]
      else items.value.push(...rows)
      if (rows.length < pageSize) finished.value = true
      else page.value += 1
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载失败'
    } finally {
      loading.value = false
    }
  }

  return { items, loading, finished, error, load }
}
