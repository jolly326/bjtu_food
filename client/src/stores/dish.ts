import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Dish, DishDetail, DishQuery, HotSearch } from '@/types/dish'
import type { Review } from '@/types/review'
import type { CanteenInfo } from '@/types/canteen'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import * as canteenApi from '@/api/canteen'
import type { FilterTab } from '@/types/filter-tab'

/** 首页筛选流单页条数（MP-05 常量化：fetchFilterDishes / loadMoreFilterDishes 共用，防口径漂移） */
export const FILTER_PAGE_SIZE = 10
/**
 * 首页筛选流最大保留页数（MP-05）：10 页 × 10 条 = 100 条封顶。
 * 此前 pageSize=10 无限 concat，深翻后 filterList 无上限增长，
 * 而 HomeContent 的 splitList 每次都全量重算 → 低端机掉帧。
 * 到底后不再静默截断，改由 filterPageLimited 驱动页面给出「已展示前 N 个结果」提示。
 */
export const FILTER_MAX_PAGES = 10

/** loading key：首页筛选流首屏 / 切筛选（MP-01：供 HomeContent 渲染骨架块） */
export const LOADING_KEY_FILTER = 'filter'
/**
 * loading key：首页筛选流触底加载更多（MP-01）。
 * 2026-09-20（任务 6.3 零消费扫描）：外部零引用（唯一消费方为 store 内的 filterLoadingMore 派生），
 * 常量保留为**模块私有**，不再导出（PR-05）。
 */
const LOADING_KEY_FILTER_MORE = 'filterMore'
/**
 * 评价列表在途登记 key（模块私有）。
 * P0-06（2026-09-20）：原导出的 `LOADING_KEY_REVIEWS` 唯一外部消费方（详情页评价区 loading 骨架态）
 * 已随「不设加载骨架/指示」红线删除，key 收敛为模块内部登记用，不再导出（PR-05）。
 */
const REVIEWS_LOADING_KEY = 'fetchReviews'

/**
 * 首页默认筛选流（热度流 / 未选食堂）：首屏初始态与「清除筛选」后唯一对应的 tab。
 * MP-03：由 store 统一提供，页面不再自持一份 defaultTab，消除「页面选中态与 store filterTab」双源。
 */
export function defaultFilterTab(): FilterTab {
  return { key: 'all', label: '全部', type: 'recommend' }
}

export const useDishStore = defineStore('dish', () => {
  const currentDish = ref<DishDetail | null>(null)
  const reviewList = ref<Review[]>([])
  /**
   * 详情首屏/刷新是否失败（失败 ≠ 加载中 ≠ 不存在）：
   * 供详情页区分「静默加载中（空白）」与「请求失败（明确文案 + 重试/返回）」两种态。
   */
  const detailError = ref(false)
  /**
   * 在途请求登记：单一 loading 被多个并发请求共享会互相提前解除（S-6）。
   * 改用 Set 记录各业务请求 key，并**必须是响应式 Set**——此前为普通 Set，
   * `computed(() => inFlight.size > 0)` 取不到依赖，首次求值后再不更新（MP-04 根因之一）。
   */
  const inFlight = ref<Set<string>>(new Set())

  /**
   * 按 key 派生在途态（MP-04）：消费方只订阅自己关心的那一个请求。
   * 取代原「全局聚合 loading」——详情页曾把「是否有任意 dish 请求在飞」
   * 当成「评价在加载」，导致一次 search/fetchDetail 就让评价区显骨架。
   */
  function isLoading(key: string): boolean {
    return inFlight.value.has(key)
  }

  /** 包裹异步请求：进入时登记 key，结束（成功/失败）时移除，保证并发互不干扰 */
  async function withLoading<T>(key: string, fn: () => Promise<T>): Promise<T> {
    inFlight.value.add(key)
    try {
      return await fn()
    } finally {
      inFlight.value.delete(key)
    }
  }
  const canteenList = ref<CanteenInfo[]>([])
  /** 食堂字典最近一次成功拉取时间（ms）；0 = 从未成功。供后台刷新节流判断 */
  let canteensFetchedAt = 0
  /** 后台刷新节流窗口：进程常驻（tabBar 页不销毁）期间回到首页最多每 5 分钟重拉一次字典 */
  const CANTEEN_REFRESH_INTERVAL_MS = 5 * 60 * 1000
  /** 后台刷新进行中标志：onShow 高频触发时防重复请求 */
  let canteensRefreshing = false

  /** 首页筛选 Bar：食堂/维度筛选选中态（选中即换内容） */
  const filterTab = ref<FilterTab | null>(null)
  const filterList = ref<Dish[]>([])
  const filterPage = ref(1)
  /** 首页价格筛选区间（元，null/undefined 表示不限）；直接透传 api（api 层统一元→分），禁止二次换算/裸算 /100 */
  const filterPrice = ref<{ min?: number; max?: number }>({})
  /**
   * 触底加载更多是否在途（MP-01）：派生自 LOADING_KEY_FILTER_MORE，不再是手工置位的布尔——
   * 既作为 loadMore 的并发守卫，也可被页面直接消费（此前手工布尔全仓零消费，属死标志）。
   */
  const filterLoadingMore = computed(() => isLoading(LOADING_KEY_FILTER_MORE))
  const filterFinished = ref(false)
  /**
   * 是否已触达保留页数上限（MP-05）：true 时不再 concat 新页，由页面给触底提示，
   * 避免「无限加载」在低端机拖垮渲染，也避免「静默截断」让用户以为没有更多。
   */
  const filterPageLimited = ref(false)
  /**
   * 首页筛选流最近一次请求是否失败（MP-012）：失败 ≠ 空数据。
   * 首页列表区据其渲染「加载失败 · 点击重试」块，替代此前「静默吞错 → 空态/无菜品误导」；
   * 过期响应（seq 失效）不修改本状态，由最新一次请求决定。
   */
  const filterError = ref(false)

  /** task-02 榜单数据 */
  const hotSearchList = ref<HotSearch[]>([])

  /** task-03 评价分页 */
  const reviewTotal = ref(0)
  /**
   * 评价首屏/刷新是否失败（PR-03 失败态）：失败 ≠ 零评价。
   * 此前失败被静默吞成「暂无评价」，用户误以为确实没人评；
   * 现由详情页评价卡据其渲染可重试失败态（分页失败仍静默，可再触底重试）。
   * 过期响应（seq 失效）不修改本状态，由最新一次请求决定。
   */
  const reviewError = ref(false)

  /** 评价请求序号：排序切换/翻页/进新菜品时丢弃过期响应，防触底 append 与 reset 交错（对齐 filterFetchSeq 模式） */
  let reviewFetchSeq = 0

  async function fetchCanteens(options?: { keepOnFail?: boolean }) {
    try {
      canteenList.value = await canteenApi.getCanteenList()
      canteensFetchedAt = Date.now()
    } catch (e) {
      console.error('加载食堂列表失败', e)
      // keepOnFail（后台静默刷新用）：失败保留旧列表不清空，避免把可用字典刷没；
      // 首次拉取维持原清空语义（渲染空态，由重试路径补拉）
      if (!options?.keepOnFail) canteenList.value = []
    }
  }

  /**
   * 食堂/档口字典最小失效机制（保证管理端改名最终可见，不追求实时）：
   * 距上次成功拉取超过节流窗口才后台重拉，失败保留旧列表（keepOnFail）。
   * 供常驻页（首页 onShow）调用——tabBar 页不销毁，此前仅 onLoad「空才拉」，
   * 进程存活期间字典永不更新（PUT /admin/canteens|stalls/{id} 改名需杀进程才可见）。
   * 字典无 storage 持久化，冷启动必拉新，本机制覆盖的只是「进程常驻热启动」窗口。
   */
  async function refreshCanteensIfStale() {
    if (canteensRefreshing) return
    if (canteensFetchedAt > 0 && Date.now() - canteensFetchedAt < CANTEEN_REFRESH_INTERVAL_MS) return
    canteensRefreshing = true
    try {
      await fetchCanteens({ keepOnFail: true })
    } finally {
      canteensRefreshing = false
    }
  }

  async function search(query: DishQuery): Promise<Dish[]> {
    try {
      return await withLoading('search', async () => await dishApi.searchDishes(query))
    } catch (e) {
      // MP-012：失败不再静默吞成空数组（会被误读为「没有结果」）——向上抛错，
      // 由唯一消费方（find 搜索流）的 catch 区分「失败」与「无结果」
      console.error('搜索失败', e)
      throw e
    }
  }

  async function fetchDetail(id: number) {
    return withLoading('fetchDetail', async () => {
      currentDish.value = await dishApi.getDishDetail(id)
      detailError.value = false
    }).catch((e) => {
      console.error('加载菜品详情失败', e)
      currentDish.value = null
      // 失败 ≠ 加载中：置位供详情页给「明确文案 + 重试/返回」恢复路径（1.7）
      detailError.value = true
    })
  }

  /** 进入新菜品前清空旧详情与评价态，避免闪现上一道菜（store 全局状态残留）。统一走 action 而非外部直接写 ref。 */
  function resetDishDetail() {
    // 使所有在途评价请求失效：旧菜品的触底 append 晚到时不再写入新菜品列表（竞态守卫，对齐 fetchReviews 注释）
    reviewFetchSeq++
    currentDish.value = null
    detailError.value = false
    reviewList.value = []
    reviewTotal.value = 0
    reviewError.value = false
  }

  /**
   * 清空评价列表与总数：供「只看有图」切换时先清后拉（避免旧口径结果在新请求返回前短暂残留）。
   * 仅重置数据，不触碰分页序号（竞态守卫由 fetchReviews 的 reviewFetchSeq 自增接管）。
   */
  function clearReviews() {
    reviewList.value = []
    reviewTotal.value = 0
  }

  /**
   * 评价区分页（RESTful 子资源 `GET /dishes/{id}/reviews`）。
   * 排序唯一为时间倒序（新评价在前），端上不传 sort、不持有排序状态（PR-02：后端唯一权威方）。
   * `hasImage=true` 走服务端「只看有图」筛选（total 同口径统计）。
   * 返回结果写入 reviewList/reviewTotal。
   * 竞态守卫（reviewFetchSeq，对齐 filterFetchSeq 模式）：触底 append 与进新菜品
   * 的 reset 交错时，过期响应直接丢弃不写入，防旧页数据 append 污染新列表。
   * **契约（唯一）**：过期 / 失败一律返回 `null` —— 调用方据此跳过分页推进。
   * 分页（append）失败同样返回 `null`（不返回数据对象），否则调用侧 `if (!res) return` 失效会
   * 递增页码，导致该页评价被**永久跳过**；失败后列表保持原样静默，用户可再次触底重试。
   */
  async function fetchReviews(
    dishId: number,
    options?: { page?: number; pageSize?: number; append?: boolean; hasImage?: boolean },
  ): Promise<{ list: Review[]; total: number } | null> {
    const seq = ++reviewFetchSeq
    const page = options?.page ?? 1
    const pageSize = options?.pageSize ?? 20
    try {
      const res = await withLoading(REVIEWS_LOADING_KEY, async () =>
        await reviewApi.getDishReviews(dishId, { page, pageSize, hasImage: options?.hasImage }))
      // 过期响应（期间又有新请求发起 / resetDishDetail 已切菜品）：丢弃，不覆盖最新列表
      if (seq !== reviewFetchSeq) return null
      if (options?.append) {
        reviewList.value = [...reviewList.value, ...res.list]
      } else {
        reviewList.value = res.list
      }
      reviewTotal.value = res.total
      // 成功写回：清除失败态（重试成功后失败块消失）
      reviewError.value = false
      return res
    } catch (e) {
      console.error('加载评价失败', e)
      // 过期请求的失败不置状态（由最新一次请求决定）
      if (seq !== reviewFetchSeq) return null
      if (!options?.append) {
        reviewList.value = []
        reviewTotal.value = 0
        // PR-03 失败态：首屏/刷新失败置位（分页失败保持静默，不打断已有列表）
        reviewError.value = true
      }
      // 失败一律返回 null（含 append 分页失败）：调用方跳过分页推进，不跳页、可再触底重试
      return null
    }
  }

  /** task-02 热搜 TOP10（派生热度词条） */
  async function fetchHotSearch() {
    try {
      hotSearchList.value = await dishApi.getHotSearch()
    } catch (e) {
      console.error('加载热搜失败', e)
      hotSearchList.value = []
    }
  }

  /** 筛选请求序号：快速切换筛选条件时丢弃过期响应，避免旧请求晚到覆盖新列表（P0 竞态修复） */
  let filterFetchSeq = 0

  /** 首页价格筛选：写回区间并刷新当前筛选流（reset 翻页从头） */
  async function setHomePrice(range: { min?: number; max?: number }) {
    filterPrice.value = range
    const tab = filterTab.value
    if (tab) await fetchFilterDishes(tab, true)
  }

  /**
   * 首页筛选：按选中食堂拉取菜品列表，复用现有分页。
   * MP-01：整体纳入 withLoading(LOADING_KEY_FILTER)——此前只有 search/fetchDetail/fetchReviews
   * 登记 inFlight，筛选流请求期间「无任何在途态」，首屏/切筛选时列表区空白，
   * 首页只能先渲染贡献卡「想吃啥没找到？告诉我们」，把「加载中」误导成「没内容」。
   */
  async function fetchFilterDishes(tab: FilterTab, reset = false) {
    return withLoading(LOADING_KEY_FILTER, async () => {
      const seq = ++filterFetchSeq
      if (reset) {
        filterList.value = []
        filterPage.value = 1
        filterFinished.value = false
        filterPageLimited.value = false
        // 新一次查询开始：先清上次失败态（成功后本就为 false；若本次失败会再置 true）
        filterError.value = false
      }
      filterTab.value = tab
      try {
        const pageSize = FILTER_PAGE_SIZE
        let rows: Dish[] = []
        // 端上不传 sortBy/sortOrder：列表顺序唯一由后端排序口径决定（§7.17 第 2 条「热度优先」；PR-02）
        if (tab.type === 'canteen' && tab.canteenId != null) {
          // 按食堂过滤：canteenId → 后端 /dishes?canteenId=，顺序由后端决定
          const res = await dishApi.searchDishesPage({ canteenId: tab.canteenId, page: filterPage.value, pageSize, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max })
          rows = res.list
        } else {
          // 默认流：热度优先（后端口径）
          const res = await dishApi.getHotDishesPage(filterPage.value, pageSize, filterPrice.value)
          rows = res.list
        }
        // 过期响应（期间又切换了筛选条件）直接丢弃，不覆盖新列表
        if (seq !== filterFetchSeq) return
        if (reset) {
          filterList.value = rows
        } else {
          filterList.value = filterList.value.concat(rows)
        }
        // 成功写回：清除失败态（MP-012，重试成功后错误块消失）
        filterError.value = false
        // 分页结束判据基于「本页返回条数 < pageSize」，避免 recommend 本地排序后 total 语义不一致导致误判到底
        if (rows.length < pageSize) filterFinished.value = true
      } catch (e) {
        // MP-012：不再静默——置 filterError 供首页列表区渲染「加载失败 · 点击重试」块；
        // 过期请求不置位（由最新一次请求决定状态），恢复走重试块 @tap 或下拉刷新
        if (seq !== filterFetchSeq) return
        console.error('加载筛选菜品失败', e)
        filterError.value = true
      }
    })
  }

  /**
   * 首页筛选触底加载更多。
   * MP-05：页数达 FILTER_MAX_PAGES 后不再 concat —— filterList 无上限增长会让
   * HomeContent 的 splitList 每次全量重算（深翻后低端机掉帧）；到底时置
   * filterPageLimited，由页面给出「已展示前 N 个结果」提示，而不是静默截断。
   */
  async function loadMoreFilterDishes(): Promise<boolean> {
    const tab = filterTab.value
    if (!tab || filterLoadingMore.value || filterFinished.value) return false
    if (filterPage.value >= FILTER_MAX_PAGES) {
      filterFinished.value = true
      filterPageLimited.value = true
      return false
    }
    // 与 fetchFilterDishes 共用 filterFetchSeq：切换筛选条件会使其自增，使在途的旧条件第 2 页结果失效，
    // 避免「切换条件时旧结果第 2 页晚到 concat 进新列表」的竞态（P0 修复）
    const seq = ++filterFetchSeq
    filterPage.value += 1
    // MP-01：触底加载同样登记在途 key（filterLoadingMore 即派生自它，不再手工置位布尔）
    return withLoading(LOADING_KEY_FILTER_MORE, async () => {
      try {
        const pageSize = FILTER_PAGE_SIZE
        let rows: Dish[] = []
        // 与 fetchFilterDishes 一致：端上不传 sortBy，顺序由后端口径决定（PR-02）
        if (tab.type === 'canteen' && tab.canteenId != null) {
          const res = await dishApi.searchDishesPage({ canteenId: tab.canteenId, page: filterPage.value, pageSize, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max })
          rows = res.list
        } else {
          const res = await dishApi.getHotDishesPage(filterPage.value, pageSize, filterPrice.value)
          rows = res.list
        }
        // 过期响应（期间又切换了筛选条件）丢弃，不混入新列表
        if (seq !== filterFetchSeq) {
          filterPage.value -= 1
          return false
        }
        filterList.value = filterList.value.concat(rows)
        // 分页结束判据基于「本页返回条数 < pageSize」（见 fetchFilterDishes 说明）
        if (rows.length < pageSize) {
          filterFinished.value = true
        } else if (filterPage.value >= FILTER_MAX_PAGES) {
          // 恰好翻满保留页数上限：置位提示，避免用户继续触底却毫无反馈
          filterFinished.value = true
          filterPageLimited.value = true
        }
        return rows.length > 0
      } catch (e) {
        console.error('加载更多筛选菜品失败', e)
        filterPage.value -= 1
        return false
      }
    })
  }

  /**
   * 清除全部首页筛选（食堂 + 价格）→ **只发一次**列表请求（MP-03）。
   * 此前页面侧连续调用 setHomePrice({}) → onCanteenSelect(null)，
   * 二者各触发一次 fetchFilterDishes，一次「清除筛选」打出多次请求，
   * 且中间两帧 filterList 被清空重建（列表区闪白）。
   */
  async function clearHomeFilter() {
    filterPrice.value = {}
    await fetchFilterDishes(defaultFilterTab(), true)
  }

  return {
    currentDish, reviewList, detailError,
    canteenList,
    hotSearchList, reviewTotal, reviewError,
    // MP-04：不再导出全局聚合 loading（任何 dish 请求在飞都会为真，消费方无法区分），
    // 改由 isLoading(key) 按业务请求订阅（key 常量见本文件顶部 LOADING_KEY_*）。
    // MP-05/MP-06：filterTotal（只写不读）已删；filterPage/filterFinished 为内部分页游标，
    // 零外部消费，收敛为模块私有（不再出现在 store 返回对象）。
    isLoading,
    filterTab, filterList, filterLoadingMore, filterPageLimited, filterPrice, filterError,
    setHomePrice, clearHomeFilter, defaultFilterTab,
    fetchCanteens, refreshCanteensIfStale, search, fetchDetail, resetDishDetail, fetchReviews, clearReviews,
    fetchHotSearch,
    fetchFilterDishes, loadMoreFilterDishes,
  }
})
