/**
 * 搜索结果态：请求 / 分页 / 三态判定。发现态与结果态的切换规则见 `useFindState`。
 *
 * 竞态与三态的口径：
 * - 守卫（`createSeqGuard`，单一真源）保证慢响应不得覆盖后发的请求；
 * - `searchDone`（请求已完成，含失败）与 `searchFailed` 组合出「加载中 / 失败 / 无结果」三态 ——
 *   失败**不得**伪装成空结果，否则用户会被误导去「推荐这道菜」而不是重试。
 */
import { ref } from 'vue'
import type { DishListItem, MixedResultItem } from '@/types/dish'
import { RESULT_PAGE_SIZE } from '@/constants/paging'
import { joinLocation } from '@/utils/dish'
import { createSeqGuard, mergePagedRows } from '@/composables/usePagedList'
import { useDishStore } from '@/stores/dish'

/** 菜品行 → 结果卡行（图片取列表字段 coverImage，位置行走 utils/dish.joinLocation） */
function toResults(list: DishListItem[]): MixedResultItem[] {
  return list
    .map((d) => ({
      type: 'dish' as const,
      id: d.id,
      name: d.name,
      // 列表 VO 不含 images 数组，无图时 coverImage 为空串
      image: d.coverImage || '',
      sub: joinLocation(d.canteen, d.stallName),
      price: d.price,
      rating: d.rating,
      originalPrice: d.originalPrice,
    }))
    .filter((r) => r.name)
}

export function useSearchResults() {
  const dishStore = useDishStore()

  /** 结果态是否已激活（与「发现态」互斥） */
  const inFilter = ref(false)
  /** 请求是否已完成（成功/失败均置真；过期请求不置）：区分「静默加载中」与「无结果」 */
  const searchDone = ref(false)
  /** 最近一次已完成搜索是否失败：失败 ≠ 无结果，渲染重试块而非空态 */
  const searchFailed = ref(false)
  /** 提交中（驱动「搜索」按钮禁用态）：仅最新一次请求可清除 */
  const searching = ref(false)
  /** 触底加载在途（与首屏 searching 分离：分页失败静默，不打断滚动） */
  const loadingMore = ref(false)
  /** 结果是否已到底 */
  const resultsFinished = ref(false)
  const mixedResults = ref<MixedResultItem[]>([])

  /** 竞态守卫：仅最新一次请求可写状态；退出结果态时 invalidate 使在途旧请求失效 */
  const guard = createSeqGuard()
  /** 当前已加载到的页码（新搜索重置为 1，触底 +1） */
  let resultPage = 1

  /** 进入结果态并检索（发现态点词条与输入框提交共用此入口） */
  async function search(keyword: string) {
    if (!keyword) return
    // 不设防重入锁：守卫已保证后发请求覆盖先发结果；加锁会让用户连续搜索新词时被静默丢弃
    const seq = guard.begin()
    inFilter.value = true
    searchDone.value = false
    searchFailed.value = false
    searching.value = true
    try {
      // 复用 store.search（GET /dishes?keyword，金额/图片已在 api 层归一）；端上不传筛选 / 排序参数
      const list = await dishStore.search({
        keyword,
        page: 1,
        pageSize: RESULT_PAGE_SIZE,
      })
      if (!guard.isCurrent(seq)) return
      // 结果顺序即后端返回口径（端上不排序、不算距离）
      mixedResults.value = toResults(list)
      resultPage = 1
      resultsFinished.value = list.length < RESULT_PAGE_SIZE
      searchDone.value = true
    } catch (err) {
      console.error('[find] 搜索失败', err)
      if (!guard.isCurrent(seq)) return
      mixedResults.value = []
      resultPage = 1
      resultsFinished.value = false
      searchDone.value = true
      searchFailed.value = true
    } finally {
      if (guard.isCurrent(seq)) searching.value = false
    }
  }

  /** 重试当前检索（失败块 @tap；竞态守卫在 search 内） */
  function retry(keyword: string) {
    return search(keyword.trim())
  }

  /** 触底加载下一页（结果态 scroll-view 的 @scrolltolower） */
  async function loadMore(keyword: string) {
    // 到底 / 分页在途 / 首屏在途 均跳过（首屏在途时页码尚未落定，避免错位）
    if (resultsFinished.value || loadingMore.value || searching.value) return
    const kw = keyword.trim()
    if (!kw) return
    // 分页**不取号**：只借当前号判定自己是否已被新搜索淘汰（取号会使在途首屏响应被误判过期）
    const seq = guard.peek()
    loadingMore.value = true
    try {
      const list = await dishStore.search({
        keyword: kw,
        page: resultPage + 1,
        pageSize: RESULT_PAGE_SIZE,
      })
      // 期间若发起了新搜索或退出结果态，丢弃本次过期结果
      if (!guard.isCurrent(seq)) return
      resultPage += 1
      // 合并 + 封底（单一真源 mergePagedRows）：去重追加、0 长度封口、满页未封底
      const merged = mergePagedRows(mixedResults.value, toResults(list), RESULT_PAGE_SIZE)
      mixedResults.value = merged.rows
      resultsFinished.value = merged.finished
    } catch (err) {
      // 分页失败静默（不置失败态、不打断滚动），再次触底即重试同一页
      console.error('[find] 结果分页加载失败', err)
    } finally {
      loadingMore.value = false
    }
  }

  /** 退出结果态回发现态：作废在途旧请求，避免其返回后写回结果造成数据残留 */
  function exit() {
    inFilter.value = false
    mixedResults.value = []
    searchDone.value = false
    searchFailed.value = false
    resultsFinished.value = false
    resultPage = 1
    guard.invalidate()
  }

  return {
    inFilter,
    searchDone,
    searchFailed,
    searching,
    loadingMore,
    resultsFinished,
    mixedResults,
    search,
    retry,
    loadMore,
    exit,
  }
}
