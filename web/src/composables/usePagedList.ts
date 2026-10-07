import { computed, ref, shallowRef } from 'vue'
import { createListState } from './listState'
import type { AdminPage } from '@/types/common'

/**
 * 管理端分页组合式（**页码 + 共 N 条**，非「加载更多」）。
 *
 * 契约真源：`AdminPageResult` = `{ records, total }`（[api/README](../../../docs/api/README.md)）；
 * 结束判据 = **`total`**（不再靠「本页条数 < pageSize」推断）。
 * 状态口径见 {@link createListState}；分页列表页另有第 ⑤ 态 `total/pageCount`。
 */
export function usePagedList<T>(
  fetcher: (page: number, pageSize: number) => Promise<AdminPage<T>>,
  pageSize = 20,
) {
  const items = shallowRef<T[]>([])
  const total = ref(0)
  const page = ref(1)
  const state = createListState(items)

  /** 总页数（`total = 0` 时返回 1，调用方以 `total === 0` 判定是否渲染分页条） */
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

  /**
   * 加载指定页。
   * @param targetPage 目标页（默认当前页；筛选 / 重置请显式传 `1`）
   */
  async function load(targetPage: number = page.value): Promise<void> {
    if (state.loading.value) return
    state.begin()
    try {
      const res = await fetcher(Math.max(1, targetPage), pageSize)
      items.value = [...res.records]
      total.value = res.total
      page.value = Math.max(1, targetPage)
    } catch (e) {
      state.fail(e)
    } finally {
      state.settle()
    }
  }

  /**
   * 重新加载**当前页**（保存 / 删除 / 启停 / 处置后调用：保持筛选与页码）。
   * <p>
   * 🔴 **末页删空自动回退**：删除会令 `total` 变小，若当前页已越界，接口会返回空记录
   * ⇒ 列表误显「暂无数据」且用户被卡在越界页。这里在首次加载后按**刷新过的** `pageCount`
   * 判断，越界则再加载最后一页（仅边界场景多发一次请求）。
   */
  async function reload(): Promise<void> {
    await load(page.value)
    if (page.value > pageCount.value) {
      await load(pageCount.value)
    }
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
    loading: state.loading,
    firstLoading: state.firstLoading,
    isEmpty: state.isEmpty,
    hasData: state.hasData,
    error: state.error,
    sessionInvalid: state.sessionInvalid,
    load,
    reload,
    reloadFirstPage,
    goToPage,
    prevPage,
    nextPage,
  }
}
