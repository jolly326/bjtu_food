import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { DishListItem, DishDetail, DishQuery, GuessLike, MealType } from '@/types/dish'
import type { Review } from '@/types/review'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import { isResourceNotFound } from '@/api/http'

/**
 * 首页列表单页条数（`fetchHomeDishes` / `loadMoreHomeDishes` 共用，防口径漂移）。
 * 2026-09-22：原「筛选流」已随食堂 / 价格筛选全量下线（K1/K2/K3），本常量即首页唯一列表流的分页口径。
 */
export const HOME_PAGE_SIZE = 10
/**
 * 首页列表最大保留页数：10 页 × 10 条 = 100 条封顶。
 * 深翻后 `homeList` 无上限增长会让 `HomeContent` 的列分配每次全量重算（低端机掉帧）；
 * 到底不再静默截断，改由 `homePageLimited` 驱动页面给出「已展示前 N 个结果」提示。
 */
export const HOME_MAX_PAGES = 10

/** loading key：首页列表首屏 / 切大类（供 `HomeContent` 判定「静默加载中」） */
export const LOADING_KEY_HOME = 'home'
/** loading key：首页列表触底加载更多（模块私有；对外由 `homeLoadingMore` 派生） */
const LOADING_KEY_HOME_MORE = 'homeMore'
/**
 * loading key：**切大类**（模块私有）。
 * 与 `LOADING_KEY_HOME` 分开登记，是为了让 `HomeContent` 的「静默加载中」判定（只订阅 `LOADING_KEY_HOME`）
 * **不被切大类触发**——切大类时**保留旧列表在屏**，否则列表被清空 + 内容塌成 0 会让
 * `scroll-view` 把滚动位置钳回顶部（用户可见 bug：切标签弹回首页顶部）。
 */
const LOADING_KEY_HOME_SWAP = 'homeSwap'
/**
 * 评价列表在途登记 key（模块私有）：
 * 原导出的 `LOADING_KEY_REVIEWS` 唯一外部消费方（详情页评价区骨架态）已随「不设加载骨架」红线删除。
 */
const REVIEWS_LOADING_KEY = 'fetchReviews'

export const useDishStore = defineStore('dish', () => {
  const currentDish = ref<DishDetail | null>(null)
  const reviewList = ref<Review[]>([])
  /**
   * 详情首屏/刷新是否失败（失败 ≠ 加载中 ≠ 不存在）：
   * 供详情页区分「静默加载中（空白）」与「请求失败（明确文案 + 重试/返回）」两种态。
   */
  const detailError = ref(false)
  /**
   * 菜品**不存在**（后端 `4001`，2026-09-23 §7.40 R8）—— 与「请求失败」**区别对待**：
   * 不存在（含已下架，下架对外等价于不存在）**不可重试**，页面应只给「返回」路径；
   * 网络 / 服务端故障才给「重新加载」。二者**互斥**（同一时刻至多一个为 true）。
   */
  const detailNotFound = ref(false)
  /**
   * 在途请求登记：单一 loading 被多个并发请求共享会互相提前解除（S-6）。
   * 必须是**响应式 Set**，否则 `computed(() => inFlight.size > 0)` 取不到依赖。
   */
  const inFlight = ref<Set<string>>(new Set())

  /** 按 key 派生在途态：消费方只订阅自己关心的那一个请求 */
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

  // ==================== 首页列表流（2026-09-22：食堂 / 价格筛选已全量下线） ====================

  /** 菜品大类字典（`GET /dishes/meal-types`）：文案 / 顺序 / 子集全由后端下发，端上零写死 */
  const mealTypeList = ref<MealType[]>([])
  /** 当前选中大类键（`null` = 全部 = 不传 `mealType`）；端上唯一保留的筛选维度 */
  const filterMealType = ref<string | null>(null)

  /** 首页列表（当前大类下的热度流；未选大类 = 全部） */
  const homeList = ref<DishListItem[]>([])
  const homePage = ref(1)
  /** 触底加载更多是否在途（派生自 loading key，兼作 loadMore 并发守卫） */
  const homeLoadingMore = computed(() => isLoading(LOADING_KEY_HOME_MORE))
  const homeFinished = ref(false)
  /** 是否已触达保留页数上限：true 时不再 concat 新页，由页面给触底提示 */
  const homePageLimited = ref(false)
  /** 列表最近一次请求是否失败（失败 ≠ 空数据）；过期响应不修改本状态 */
  const homeError = ref(false)

  /** 列表请求序号：快速切换大类时丢弃过期响应，避免旧请求晚到覆盖新列表 */
  let homeFetchSeq = 0

  /**
   * 拉取菜品大类字典（失败降级为空数组 → 标签栏只剩「全部」，不阻塞首屏列表）。
   * 顺带校正选中项：若所选大类已不在字典（该类当前无在售菜）→ 回落「全部」，避免请求一个空类。
   */
  async function fetchMealTypes() {
    try {
      mealTypeList.value = await dishApi.getMealTypes()
      if (filterMealType.value && !mealTypeList.value.some((m) => m.key === filterMealType.value)) {
        filterMealType.value = null
      }
    } catch (e) {
      console.error('加载菜品大类失败', e)
      mealTypeList.value = []
    }
  }

  /**
   * 切换大类标签：写回选中键并重置分页刷新列表。
   * **不重置页面滚动位置**（UI 文档 §5 边界行为）：因此走 `keepList = true` —— 新数据到手前
   * 旧列表留在屏上（stale-while-revalidate），避免内容塌陷把滚动位置钳到顶部。
   */
  async function setHomeMealType(key: string | null) {
    filterMealType.value = key
    await fetchHomeDishes(true, true)
  }

  /**
   * 首页列表拉取（`reset=true` 表示切大类 / 首屏 / 重试：清列表、回到第 1 页）。
   * 端上**不传排序参数**（排序恒为服务端热度倒序），也不传任何食堂 / 价格条件（已下线）。
   */
  async function fetchHomeDishes(reset = false, keepList = false) {
    return withLoading(keepList ? LOADING_KEY_HOME_SWAP : LOADING_KEY_HOME, async () => {
      const seq = ++homeFetchSeq
      if (reset) {
        // `keepList`（切大类）= 旧列表留在屏上，只重置分页状态；其余场景（首屏 / 重试）清列表
        if (!keepList) homeList.value = []
        homePage.value = 1
        homeFinished.value = false
        homePageLimited.value = false
        // 新一次查询开始：先清上次失败态（成功后本就为 false；若本次失败会再置 true）
        homeError.value = false
      }
      try {
        const pageSize = HOME_PAGE_SIZE
        const res = await dishApi.searchDishesPage({
          mealType: filterMealType.value ?? undefined,
          page: homePage.value,
          pageSize,
        })
        // 过期响应（期间又切换了大类）直接丢弃，不覆盖新列表
        if (seq !== homeFetchSeq) return
        homeList.value = reset ? res.list : homeList.value.concat(res.list)
        homeError.value = false
        // 结束判据基于「本页返回条数 < pageSize」
        if (res.list.length < pageSize) homeFinished.value = true
      } catch (e) {
        if (seq !== homeFetchSeq) return
        console.error('加载菜品列表失败', e)
        homeError.value = true
      }
    })
  }

  /**
   * 首页列表触底加载更多：页数达 `HOME_MAX_PAGES` 后不再 concat（置 `homePageLimited`）。
   */
  async function loadMoreHomeDishes(): Promise<boolean> {
    if (homeLoadingMore.value || homeFinished.value) return false
    if (homePage.value >= HOME_MAX_PAGES) {
      homeFinished.value = true
      homePageLimited.value = true
      return false
    }
    const seq = ++homeFetchSeq
    homePage.value += 1
    return withLoading(LOADING_KEY_HOME_MORE, async () => {
      try {
        const pageSize = HOME_PAGE_SIZE
        const res = await dishApi.searchDishesPage({
          mealType: filterMealType.value ?? undefined,
          page: homePage.value,
          pageSize,
        })
        if (seq !== homeFetchSeq) {
          homePage.value -= 1
          return false
        }
        homeList.value = homeList.value.concat(res.list)
        if (res.list.length < pageSize) {
          homeFinished.value = true
        } else if (homePage.value >= HOME_MAX_PAGES) {
          homeFinished.value = true
          homePageLimited.value = true
        }
        return res.list.length > 0
      } catch (e) {
        console.error('加载更多菜品失败', e)
        homePage.value -= 1
        return false
      }
    })
  }

  // ==================== 搜索（find 页） ====================

  async function search(query: DishQuery): Promise<DishListItem[]> {
    try {
      return await withLoading('search', async () => await dishApi.searchDishes(query))
    } catch (e) {
      // MP-012：失败不再静默吞成空数组（会被误读为「没有结果」）——向上抛错，
      // 由唯一消费方（find 搜索流）的 catch 区分「失败」与「无结果」
      console.error('搜索失败', e)
      throw e
    }
  }

  // ==================== 详情与评价 ====================

  async function fetchDetail(id: number) {
    return withLoading('fetchDetail', async () => {
      currentDish.value = await dishApi.getDishDetail(id)
      detailError.value = false
      detailNotFound.value = false
    }).catch((e) => {
      console.error('加载菜品详情失败', e)
      currentDish.value = null
      // 4001（资源不存在，R8）→ 不存在态（不可重试）；其余（网络 / 5xx）→ 失败态（可重试）
      detailNotFound.value = isResourceNotFound(e)
      detailError.value = !detailNotFound.value
    })
  }

  /** 进入新菜品前清空旧详情与评价态，避免闪现上一道菜（store 全局状态残留） */
  function resetDishDetail() {
    // 使所有在途评价请求失效：旧菜品的触底 append 晚到时不再写入新菜品列表（竞态守卫）
    reviewFetchSeq++
    currentDish.value = null
    detailError.value = false
    detailNotFound.value = false
    reviewList.value = []
    reviewTotal.value = 0
    reviewError.value = false
  }

  /** 清空评价列表与总数：供「只看有图」切换时先清后拉（避免旧口径结果短暂残留） */
  function clearReviews() {
    reviewList.value = []
    reviewTotal.value = 0
  }

  /** 评价首屏/刷新是否失败（失败 ≠ 零评价）；过期响应不修改本状态 */
  const reviewTotal = ref(0)
  const reviewError = ref(false)

  /** 评价请求序号：翻页 / 进新菜品时丢弃过期响应，防触底 append 与 reset 交错 */
  let reviewFetchSeq = 0

  /**
   * 评价区分页（RESTful 子资源 `GET /dishes/{id}/reviews`）。
   * 排序唯一为时间倒序；`hasImage=true` 走服务端「只看有图」筛选（total 同口径统计）。
   * **契约（唯一）**：过期 / 失败一律返回 `null` —— 调用方据此跳过分页推进（避免永久跳过该页）。
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
      reviewError.value = false
      return res
    } catch (e) {
      console.error('加载评价失败', e)
      if (seq !== reviewFetchSeq) return null
      if (!options?.append) {
        reviewList.value = []
        reviewTotal.value = 0
        reviewError.value = true
      }
      return null
    }
  }

  /** 猜你喜欢（随机抽取在售菜品名；find 页「猜你喜欢」区块消费） */
  const guessLikeList = ref<GuessLike[]>([])
  async function fetchGuessLike() {
    try {
      guessLikeList.value = await dishApi.getGuessLike()
    } catch (e) {
      console.error('加载猜你喜欢失败', e)
      guessLikeList.value = []
    }
  }

  return {
    // 在途态
    isLoading,
    // 首页列表流（唯一筛选维度 = 菜品大类）
    mealTypeList, filterMealType,
    homeList, homeLoadingMore, homePageLimited, homeError,
    fetchMealTypes, setHomeMealType, fetchHomeDishes, loadMoreHomeDishes,
    // 搜索 / 详情 / 评价 / 热搜
    search,
    currentDish, detailError, detailNotFound, fetchDetail, resetDishDetail,
    reviewList, reviewTotal, reviewError, fetchReviews, clearReviews,
    guessLikeList, fetchGuessLike,
  }
})
