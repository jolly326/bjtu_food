/**
 * 搜索结果态：请求 / 分页 / 三态判定。
 *
 * <p><b>为何独立成 composable</b>：原为 `pages/find/index.vue` 内约 100 行内联逻辑
 * （竞态守卫 + 首屏检索 + 触底分页 + 失败/无结果三态），与「发现态」「搜索记录」
 * 完全无关却混在同一文件，是该页 569 行的主要来源。
 *
 * <p><b>竞态守卫（`searchSeq`）</b>：慢请求结果不得覆盖后发的快请求。
 * 取自 `review.vue` 的 `searchSeq` 模式（参照实现）。仅**最新一次**请求可写状态 / 清提交中，
 * 过期请求返回时直接丢弃。
 *
 * <p><b>三态互斥</b>：`searchDone` 为「请求已完成（成功或失败）」，
 * 与 `searchFailed` 组合出「加载中 / 失败 / 无结果」三态 —— 失败**不得**伪装成空结果，
 * 否则用户会被误导去「推荐这道菜」而不是重试。
 *
 * <p><b>分页口径</b>：结束判据只能是「本页返回条数 < pageSize」——
 * 后端 `PageResult` 只下发 `records`，**无 `total`**（见 `constants/paging`）。
 * 分页失败静默回退页码（不置失败态、不打断滚动），再次触底即重试同一页。
 */
import { ref } from 'vue'
import type { DishListItem, MixedResultItem } from '@/types/dish'
import { RESULT_PAGE_SIZE } from '@/constants/paging'
import { joinLocation } from '@/utils/dish'
import { mergePagedRows } from '@/composables/usePagedList'
import { useDishStore } from '@/stores/dish'

/** 菜品行 → 结果卡行（唯一映射口径：图片取列表字段 coverImage，位置行走 utils/dish.joinLocation） */
function toResults(list: DishListItem[]): MixedResultItem[] {
  return list
    .map((d) => ({
      type: 'dish' as const,
      id: d.id,
      name: d.name,
      // 列表唯一图片字段 coverImage（列表 VO 不含 images 数组）
      image: d.coverImage || '',
      // 副信息：食堂名 + 档口名（口径统一走 utils/dish.joinLocation）
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

  /** 竞态守卫序号；退出结果态时自增使在途旧请求失效 */
  let searchSeq = 0
  /** 当前已加载到的页码（新搜索重置为 1，触底 +1） */
  let resultPage = 1

  /** 进入结果态并检索（发现态点词条与输入框提交共用此入口） */
  async function search(keyword: string) {
    if (!keyword) return
    // 不设防重入锁：竞态守卫已保证后发请求覆盖先发结果；
    // 加锁会让用户连续搜索新词时被静默丢弃、界面停留在旧结果。
    const seq = ++searchSeq
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
      if (seq !== searchSeq) return
      // 结果顺序即后端返回口径（端上不排序、不算距离）
      mixedResults.value = toResults(list)
      resultPage = 1
      resultsFinished.value = list.length < RESULT_PAGE_SIZE
      searchDone.value = true
    } catch (err) {
      console.error('[find] 搜索失败', err)
      if (seq !== searchSeq) return
      mixedResults.value = []
      resultPage = 1
      resultsFinished.value = false
      searchDone.value = true
      searchFailed.value = true
    } finally {
      if (seq === searchSeq) searching.value = false
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
    const seq = searchSeq
    loadingMore.value = true
    try {
      const list = await dishStore.search({
        keyword: kw,
        page: resultPage + 1,
        pageSize: RESULT_PAGE_SIZE,
      })
      // 期间若发起了新搜索或退出结果态，丢弃本次过期结果
      if (seq !== searchSeq) return
      resultPage += 1
      // 合并 + 封底（单一真源 mergePagedRows）：去重追加、0 长度封口、满页未封底
      const merged = mergePagedRows(mixedResults.value, toResults(list), RESULT_PAGE_SIZE)
      mixedResults.value = merged.rows
      resultsFinished.value = merged.finished
    } catch (err) {
      console.error('[find] 结果分页加载失败', err)
    } finally {
      loadingMore.value = false
    }
  }

  /** 退出结果态回发现态：自增序号使在途旧请求失效，避免其返回后写回结果造成数据残留 */
  function exit() {
    inFilter.value = false
    mixedResults.value = []
    searchDone.value = false
    searchFailed.value = false
    resultsFinished.value = false
    resultPage = 1
    searchSeq += 1
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
