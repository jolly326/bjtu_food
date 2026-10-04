import { computed, ref, type Ref } from 'vue'
import { isSessionInvalid } from '@/api/http'

/**
 * 列表状态内核：加载 / 错误（带重试）/ 空 / 会话失效（403，**不渲染重试**）四态。
 *
 * `usePagedList`（分页列表页）与 `useSimpleList`（量级极小、接口不分页的字典页）共用本内核，
 * 保证两类列表的状态判定与失败处置**逐字一致** —— 否则同一页面里两种表格的失败表现会分叉。
 */
export function createListState(items: Ref<unknown[]>) {
  const loading = ref(false)
  const error = ref<string | null>(null)
  /** 会话失效（403）：口令不匹配 —— 重试必然再失败，页面不给重试入口 */
  const sessionInvalid = ref(false)

  /** 首屏加载（无数据且在途）—— 决定整表骨架 */
  const firstLoading = computed(() => loading.value && items.value.length === 0)
  /** 空：加载完成、无错误、无数据 */
  const isEmpty = computed(
    () => !loading.value && !error.value && !sessionInvalid.value && items.value.length === 0,
  )
  /** 是否有数据可渲染（有数据时即便在途也保留旧行，避免翻页闪空） */
  const hasData = computed(() => items.value.length > 0)

  /** 发起一次加载：进入在途并清空上一轮的错误态 */
  function begin() {
    loading.value = true
    error.value = null
    sessionInvalid.value = false
  }

  /** 失败归类：403 → 会话失效（不渲染重试）；其余 → 错误文案 */
  function fail(e: unknown) {
    if (isSessionInvalid(e)) {
      sessionInvalid.value = true
    } else {
      error.value = e instanceof Error ? e.message : '加载失败'
    }
  }

  /** 结束在途（成功 / 失败均调用） */
  function settle() {
    loading.value = false
  }

  return { loading, error, sessionInvalid, firstLoading, isEmpty, hasData, begin, fail, settle }
}