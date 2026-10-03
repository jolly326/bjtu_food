import { computed, ref, shallowRef } from 'vue'
import { isSessionInvalid } from '@/api/http'
import type { AdminPage } from '@/types/common'

/**
 * 管理端分页组合式（**页码 + 共 N 条**，非「加载更多」）。
 *
 * <p>契约真源：`AdminPageResult` = `{ records, total }`（[api/README](../../../docs/api/README.md)）；
 * 结束判据 = **`total`**（不再靠「本页条数 < pageSize」推断）。
 *
 * <p><b>六态</b>（[UI 基线 §1.5](../../../docs/web/ui/公共组件与形态基线.md)）：
 * ① 加载 `loading` ② 错误 `error` ③ 空 `empty` ④ 有数据 `items` ⑤ 分页 `total/pageCount`
 * ⑥ **会话失效 `sessionInvalid`**（`403` 口令不匹配 —— 页面渲染专用态且**不渲染重试**）。
 */
export function usePagedList<T>(
  fetcher: (page: number, pageSize: number) => Promise<AdminPage<T>>,
  pageSize = 20,
) {
  const items = shallowRef<T[]>([])
  const total = ref(0)
  const page = ref(1)
  const loading = ref(false)
  const error = ref<string | null>(null)
  /** ⑥ 会话失效（403）：口令不匹配 / 账号受限 —— 重试必然再失败，故页面不给重试入口 */
  const sessionInvalid = ref(false)

  /** 总页数（`total = 0` 时返回 1，调用方以 `total === 0` 判定是否渲染分页条） */
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

  /** 是否处于「首屏加载」（无数据且在途）—— 决定整表骨架 / 状态盒 */
  const firstLoading = computed(() => loading.value && items.value.length === 0)

  /** 是否处于「空」（加载完成、无错误、无数据） */
  const isEmpty = computed(
    () => !loading.value && !error.value && !sessionInvalid.value && items.value.length === 0,
  )

  /** 是否有数据可渲染（有数据时即便在途也保留旧行，避免翻页闪空） */
  const hasData = computed(() => items.value.length > 0)

  /**
   * 加载指定页。
   * @param targetPage 目标页（默认当前页；筛选 / 重置请显式传 `1`）
   */
  async function load(targetPage: number = page.value): Promise<void> {
    if (loading.value) return
    loading.value = true
    error.value = null
    sessionInvalid.value = false
    try {
      const res = await fetcher(Math.max(1, targetPage), pageSize)
      items.value = [...res.records]
      total.value = res.total
      page.value = Math.max(1, targetPage)
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

  /** 重新加载**当前页**（保存 / 删除 / 启停 / 处置后调用：保持筛选与页码） */
  function reload(): Promise<void> {
    return load(page.value)
  }

  /** 回到**第 1 页并加载**（筛选变更 / 重置后调用） */
  function reloadFirstPage(): Promise<void> {
    return load(1)
  }

  /** 翻页（翻页后滚动回表格顶部，避免长表格翻页后停在页脚） */
  function goToPage(target: number): Promise<void> {
    const clamped = Math.min(Math.max(1, target), pageCount.value)
    if (clamped === page.value) return Promise.resolve()
    const p = load(clamped)
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return p
  }

  function prevPage(): Promise<void> {
    return goToPage(page.value - 1)
  }

  function nextPage(): Promise<void> {
    return goToPage(page.value + 1)
  }

  return {
    items,
    total,
    page,
    pageSize,
    pageCount,
    loading,
    firstLoading,
    isEmpty,
    hasData,
    error,
    sessionInvalid,
    load,
    reload,
    reloadFirstPage,
    goToPage,
    prevPage,
    nextPage,
  }
}
