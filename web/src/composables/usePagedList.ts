import { computed, ref, shallowRef } from 'vue'
import { createListState } from './listState'
import { createSeqGuard } from '@/utils/seq-guard'
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
  /** 并发守卫：取号后按新旧裁决，过期响应一律丢弃（快速切筛选 / 切页时不覆盖新结果） */
  const seq = createSeqGuard()

  /** 总页数（`total = 0` 时返回 1，调用方以 `total === 0` 判定是否渲染分页条） */
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

  /**
   * 加载指定页。
   * @param targetPage 目标页（默认当前页；筛选 / 重置请显式传 `1`）
   */
  async function load(targetPage: number = page.value): Promise<void> {
    const my = seq.begin()
    state.begin()
    try {
      const res = await fetcher(Math.max(1, targetPage), pageSize)
      // 已被更新的请求淘汰 ⇒ 丢弃本次结果（不写状态、不弹错误）
      if (!seq.isCurrent(my)) return
      items.value = [...res.records]
      total.value = res.total
      page.value = Math.max(1, targetPage)
    } catch (e) {
      if (!seq.isCurrent(my)) return
      state.fail(e)
    } finally {
      state.settle()
    }
  }

  /**
   * 移除行后的落点页：**越界** ⇒ 最后一页；**本页已被删空且不在首页** ⇒ 上一页；其余 ⇒ 当前页。
   *
   * <p>`pageCount` 在 `total = 0` 时兜底为 1 ⇒ 整表删空时落点恒为第 1 页。
   * 回退只走「上一页 / 最后一页」两档，**不跳回第 1 页**（丢失浏览位置）。
   */
  function fallbackPage(): number {
    if (page.value > pageCount.value) return pageCount.value
    if (items.value.length === 0 && page.value > 1) return page.value - 1
    return page.value
  }

  /**
   * 重新加载**当前页**（保存 / 删除 / 启停 / 处置后调用：保持筛选与页码）。
   * <p>
   * 🔴 **移除行后不留在空页**：删除（或处置后不再命中当前筛选）会令 `total` 变小，
   * 越界页返回空记录 ⇒ 列表停在「空列表 + 上一页可点」。这里先按刷新后的 `total`
   * 用 {@link fallbackPage} 定落点，落点与当前页不同才再加载一次；
   * 未移除行时落点 = 当前页 ⇒ 不产生额外请求（筛选 / 翻页 / 保存路径零感知）。
   */
  async function reload(): Promise<void> {
    await load(page.value)
    const target = fallbackPage()
    if (target !== page.value) await load(target)
  }

  /** 回到**第 1 页并加载**（筛选变更 / 重置后调用） */
  function reloadFirstPage(): Promise<void> {
    return load(1)
  }

  /**
   * 翻页后回到**列表顶边**（基线 §1.6）：滚到列表容器而不是文档顶端 ——
   * 后者会把页头与筛选区一并推出视口，翻页后失去「当前筛选是什么」的上下文。
   * 动效随 `prefers-reduced-motion: reduce` 降级为瞬时跳转（基线 §1.13）。
   */
  function scrollToListTop(): void {
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
    const anchor =
      document.querySelector<HTMLElement>('.table-wrap') ??
      document.querySelector<HTMLElement>('.page')
    const top = anchor ? Math.max(0, anchor.getBoundingClientRect().top + window.scrollY - 12) : 0
    window.scrollTo({ top, behavior: reduce ? 'auto' : 'smooth' })
  }

  /** 翻页（翻页后滚动回列表顶部，避免长表格翻页后停在页脚） */
  function goToPage(target: number): Promise<void> {
    const clamped = Math.min(Math.max(1, target), pageCount.value)
    if (clamped === page.value) return Promise.resolve()
    const p = load(clamped)
    scrollToListTop()
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
