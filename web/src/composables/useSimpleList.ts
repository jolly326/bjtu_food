import { computed, ref, shallowRef } from 'vue'
import { isSessionInvalid } from '@/api/http'

/**
 * 非分页列表组合式（A1 食堂 / A2 档口 / A4 维度与取值 / A5 Banner / A6 视图与分类值 / A7 举报原因）。
 *
 * <p>这些列表**量级极小、接口不分页**（`data` 直接是数组），故与 {@link usePagedList} 分开；
 * 状态口径一致：**四态** = 加载 / 错误（带重试）/ 空 / **会话失效（403，不渲染重试）**。
 */
export function useSimpleList<T>(fetcher: () => Promise<T[]>) {
  const items = shallowRef<T[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  /** 会话失效（403）：口令不匹配 —— 重试必然再失败，页面不给重试入口 */
  const sessionInvalid = ref(false)

  const firstLoading = computed(() => loading.value && items.value.length === 0)
  const isEmpty = computed(
    () => !loading.value && !error.value && !sessionInvalid.value && items.value.length === 0,
  )
  const hasData = computed(() => items.value.length > 0)

  /** 加载 / 重新加载（写操作成功后调用即「刷新」） */
  async function load(): Promise<void> {
    if (loading.value) return
    loading.value = true
    error.value = null
    sessionInvalid.value = false
    try {
      items.value = [...(await fetcher())]
    } catch (e) {
      if (isSessionInvalid(e)) {
        sessionInvalid.value = true
      } else {
        error.value = e instanceof Error ? e.message : '加载失败'
      }
    } finally {
      loading.value = false
    }
  }

  return { items, loading, firstLoading, isEmpty, hasData, error, sessionInvalid, load }
}
