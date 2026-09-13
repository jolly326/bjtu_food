import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Dish, DishDetail, DishQuery, DishSortBy, HotSearch } from '@/types/dish'
import type { Review, ReviewSort } from '@/types/review'
import type { CanteenInfo } from '@/types/canteen'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import * as canteenApi from '@/api/canteen'
import { getRecommendDishes } from '@/api/recommend'
import { getCategories, type CategoryItem } from '@/api/category'
import { useLocationStore } from '@/stores/location'
import { haversineMeters, CAMPUS_CENTER } from '@/utils/location'
import type { FilterTab } from '@/types/filter-tab'

/** 首页排序面板选项（问题一：2026-08-31 拍板，默认「最新」，综合推荐不保留） */
export type HomeSortKey = 'latest' | 'priceAsc' | 'priceDesc' | 'hot' | 'distance'

/** 排序选项 → 后端 sortBy/sortOrder 映射（纯复用既有 DishSortBy 查询参数，无新接口） */
function sortParamsFor(key: HomeSortKey): { sortBy: DishSortBy; sortOrder: 'asc' | 'desc' } {
  switch (key) {
    case 'latest': return { sortBy: 'created_at', sortOrder: 'desc' }
    case 'priceAsc': return { sortBy: 'price', sortOrder: 'asc' }
    case 'priceDesc': return { sortBy: 'price', sortOrder: 'desc' }
    case 'hot': return { sortBy: 'heat', sortOrder: 'desc' }
    case 'distance': return { sortBy: 'heat', sortOrder: 'desc' } // 后端无法按客户端距离排序，加载后前端重排
  }
}

/** 按客户端计算距离升序（未定位/无坐标置末尾） */
function sortByDistance(rows: Dish[]): Dish[] {
  return [...rows].sort((a, b) => (a.distance ?? Number.POSITIVE_INFINITY) - (b.distance ?? Number.POSITIVE_INFINITY))
}

export const useDishStore = defineStore('dish', () => {
  const dishList = ref<Dish[]>([])
  const currentDish = ref<DishDetail | null>(null)
  const recommendList = ref<Dish[]>([])
  const guessList = ref<Dish[]>([])
  const reviewList = ref<Review[]>([])
  const stallDishes = ref<Dish[]>([])
  /**
   * 在途请求引用计数：单一 loading 被多个并发请求共享会互相提前解除（S-6）。
   * 改用 Set 记录各业务请求 key，loading 派生为"是否有请求在飞"，互不影响。
   */
  const inFlight = new Set<string>()
  const loading = computed(() => inFlight.size > 0)

  /** 包裹异步请求：进入时登记 key，结束（成功/失败）时移除，保证并发互不干扰 */
  async function withLoading<T>(key: string, fn: () => Promise<T>): Promise<T> {
    inFlight.add(key)
    try {
      return await fn()
    } finally {
      inFlight.delete(key)
    }
  }
  const navParams = { stallName: '', canteen: '' }
  const canteenList = ref<CanteenInfo[]>([])
  const newDishes = ref<Dish[]>([])
  const promotionDishes = ref<Dish[]>([])

  /** 首页筛选 Bar：品类滚轮（真实食堂品类，来自 GET /categories），选中即换内容 */
  const filterTab = ref<FilterTab | null>(null)
  const filterList = ref<Dish[]>([])
  const filterTotal = ref(0)
  const filterPage = ref(1)
  /** 首页价格筛选区间（元，null/undefined 表示不限）；直接透传 api（api 层统一元→分），禁止二次换算/裸算 /100 */
  const filterPrice = ref<{ min?: number; max?: number }>({})
  const filterLoadingMore = ref(false)
  const filterFinished = ref(false)
  /**
   * 首页筛选流最近一次请求是否失败（MP-012）：失败 ≠ 空数据。
   * 首页列表区据其渲染「加载失败 · 点击重试」块，替代此前「静默吞错 → 空态/无菜品误导」；
   * 过期响应（seq 失效）不修改本状态，由最新一次请求决定。
   */
  const filterError = ref(false)
  /** 首页排序面板当前选中项（问题一：默认「最新」，综合推荐不保留） */
  const homeSortBy = ref<HomeSortKey>('latest')

  /** 首页品类滚轮数据源（后端 category 表 enabled 品类，按 sortOrder 升序） */
  const categories = ref<CategoryItem[]>([])

  /** 拉取品类列表（首页品类滚轮数据源；失败回退空数组） */
  async function fetchCategories(): Promise<CategoryItem[]> {
    try {
      categories.value = await getCategories()
    } catch (e) {
      console.error('加载品类失败', e)
      categories.value = []
    }
    return categories.value
  }

  /** task-02 榜单数据 */
  const hotSearchList = ref<HotSearch[]>([])
  const risingDishes = ref<Dish[]>([])

  /** task-03 评价分页 */
  const reviewTotal = ref(0)
  const reviewSort = ref<ReviewSort>('latest')
  /** 评价脏标记：写评价/回复/删除成功后置 true，onShow 据此决定是否重拉，避免每次返回都发请求（#8） */
  const reviewsDirty = ref(false)

  /** 评价请求序号：排序切换/翻页/进新菜品时丢弃过期响应，防触底 append 与 reset 交错（对齐 filterFetchSeq 模式） */
  let reviewFetchSeq = 0

  async function fetchCanteens() {
    try {
      canteenList.value = await canteenApi.getCanteenList()
    } catch (e) {
      console.error('加载食堂列表失败', e)
      canteenList.value = []
    }
  }

  async function fetchRecommend() {
    return withLoading('fetchRecommend', async () => {
      recommendList.value = await dishApi.getRecommendList()
    }).catch((e) => {
      console.error('[store] fetchRecommend failed', e)
      recommendList.value = []
    })
  }

  /** 猜你喜欢：GET /dishes/recommend，未登录降级纯热度；excludeIds 去重 */
  async function fetchGuess(excludeIds: number[] = []) {
    return withLoading('fetchGuess', async () => {
      const res = await getRecommendDishes({ excludeIds, pageSize: 10 })
      guessList.value = res.list
    }).catch((e) => {
      console.error('[store] fetchGuess failed', e)
      guessList.value = []
    })
  }

  async function search(query: DishQuery): Promise<Dish[]> {
    try {
      const list = await withLoading('search', async () => await dishApi.searchDishes(query))
      dishList.value = list
      return list
    } catch (e) {
      // MP-012：失败不再静默吞成空数组（会被误读为「没有结果」）——向上抛错，
      // 由唯一消费方（find 搜索流）的 catch 区分「失败」与「无结果」；dishList 仅内部缓存，失败清空防残留
      console.error('搜索失败', e)
      dishList.value = []
      throw e
    }
  }

  /** task-02 多维筛选结果页：返回分页结果（list + total），供无限加载 */
  async function searchPage(query: DishQuery): Promise<{ list: Dish[]; total: number }> {
    try {
      const res = await withLoading('searchPage', async () => await dishApi.searchDishesPage(query))
      dishList.value = res.list
      return res
    } catch (e) {
      console.error('搜索失败', e)
      dishList.value = []
      return { list: [], total: 0 }
    }
  }

  async function fetchDetail(id: number) {
    return withLoading('fetchDetail', async () => {
      currentDish.value = await dishApi.getDishDetail(id)
    }).catch((e) => {
      console.error('加载菜品详情失败', e)
      currentDish.value = null
    })
  }

  /** 进入新菜品前清空旧详情与评价态，避免闪现上一道菜（store 全局状态残留）。统一走 action 而非外部直接写 ref。 */
  function resetDishDetail() {
    // 使所有在途评价请求失效：旧菜品的触底 append 晚到时不再写入新菜品列表（竞态守卫，对齐 fetchReviews 注释）
    reviewFetchSeq++
    currentDish.value = null
    reviewList.value = []
    reviewTotal.value = 0
  }

  /**
   * 登录态变更（登出/换用户）时清理「用户态个性化数据」：
   * - guessList（猜你喜欢，依赖登录态个性化）
   * - reviewList 中各评价的 isUseful（有用标记是当前用户维度）
   * 防止 forceLogout 后上一用户的偏好数据残留串档（§5.x 登录态一致性）。
   */
  function resetUserScopedData() {
    guessList.value = []
    for (const r of reviewList.value) {
      if (r.useful) r.useful = false
    }
  }

  /**
   * task-03 评价区重做：分页 + 排序。
   * sort: latest|useful。返回结果写入 reviewList/reviewTotal。
   * 竞态守卫（reviewFetchSeq，对齐 filterFetchSeq 模式）：触底 append 与排序切换/进新菜品
   * 的 reset 交错时，过期响应直接丢弃不写入，防旧页数据 append 污染新列表；
   * 过期/失败的请求返回 null，调用方据其跳过分页推进。
   */
  async function fetchReviews(
    dishId: number,
    options?: { sort?: ReviewSort; page?: number; pageSize?: number; append?: boolean },
  ): Promise<{ list: Review[]; total: number } | null> {
    const seq = ++reviewFetchSeq
    const sort = options?.sort ?? reviewSort.value
    reviewSort.value = sort
    const page = options?.page ?? 1
    const pageSize = options?.pageSize ?? 50
    try {
      const res = await withLoading('fetchReviews', async () =>
        await reviewApi.getReviewsByDish(dishId, { sort, page, pageSize }))
      // 过期响应（期间又有新请求发起 / resetDishDetail 已切菜品）：丢弃，不覆盖最新列表
      if (seq !== reviewFetchSeq) return null
      if (options?.append) {
        reviewList.value = [...reviewList.value, ...res.list]
      } else {
        reviewList.value = res.list
      }
      reviewTotal.value = res.total
      return res
    } catch (e) {
      console.error('加载评价失败', e)
      // 过期请求的失败不置状态（由最新一次请求决定）
      if (seq !== reviewFetchSeq) return null
      if (!options?.append) {
        reviewList.value = []
        reviewTotal.value = 0
      }
      return { list: reviewList.value, total: reviewTotal.value }
    }
  }

  async function fetchNewDishes(): Promise<Dish[]> {
    try {
      const data = await dishApi.getNewDishes()
      newDishes.value = data
      return data
    } catch (e) {
      console.error('加载上新菜品失败', e)
      newDishes.value = []
      return []
    }
  }

  async function fetchPromotionDishes(): Promise<Dish[]> {
    try {
      const data = await dishApi.getPromotionDishes()
      promotionDishes.value = data
      return data
    } catch (e) {
      console.error('加载活动菜品失败', e)
      promotionDishes.value = []
      return []
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

  /** task-02 新晋黑马 */
  async function fetchRising() {
    try {
      risingDishes.value = await dishApi.getRisingDishes()
    } catch (e) {
      console.error('加载新晋黑马失败', e)
      risingDishes.value = []
    }
  }

  /**
   * 基于坐标 + Haversine 本地写回每个菜品 distance（米）：
   * - 用户已授权定位：用真实坐标算距离；默认按距离升序排序；
   * - 未授权 / 无法获取（如 H5 预览）：回退到 CAMPUS_CENTER，距离字段始终有值（不排序，保持后端热度）。
   * - 菜品坐标缺失（旧库 canteen 无坐标 / 后端返回 null）：回退 CAMPUS_CENTER 兜底计算，
   *   保证「距你 Xm」恒有值（语义：距校区中心），避免卡片整行不显示（P0 UI 缺漏）。
   * - sort 为 false 时仅写回距离不排序（品类/tag 流保持后端热度序，卡片仍显示「距你」）。
   * 用户位置不出本机，服务器不算距离。
   */
  function withLocalDistance(list: Dish[], sort = true): Dish[] {
    const locStore = useLocationStore()
    const realLoc = locStore.location
    const loc = realLoc || CAMPUS_CENTER
    const decorated = list.map((d) => {
      const dishLoc =
        typeof d.latitude === 'number' && typeof d.longitude === 'number'
          ? { lat: d.latitude, lng: d.longitude }
          : CAMPUS_CENTER // 菜品坐标缺失兜底：距校区中心
      d.distance = haversineMeters(loc, dishLoc)
      return d
    })
    if (realLoc && sort) {
      decorated.sort((a, b) => (a.distance ?? Number.MAX_SAFE_INTEGER) - (b.distance ?? Number.MAX_SAFE_INTEGER))
    }
    return decorated
  }

  async function fetchStallDishes(stallId: number) {
    return withLoading('fetchStallDishes', async () => {
      stallDishes.value = await dishApi.getStallDishes(stallId)
    }).catch((e) => {
      console.error('加载档口菜品失败', e)
      stallDishes.value = []
    })
  }

  /** 筛选请求序号：快速切换品类时丢弃过期响应，避免旧请求晚到覆盖新品类（P0 竞态修复） */
  let filterFetchSeq = 0

  /** 首页排序：切换排序项后按新排序重载当前筛选流（问题一；复用既有 sortBy 查询参数，无新接口） */
  async function setHomeSort(key: HomeSortKey) {
    if (homeSortBy.value === key) return
    homeSortBy.value = key
    const tab = filterTab.value
    if (tab) await fetchFilterDishes(tab, true)
  }

  /** 首页价格筛选：写回区间并刷新当前筛选流（reset 翻页从头） */
  async function setHomePrice(range: { min?: number; max?: number }) {
    filterPrice.value = range
    const tab = filterTab.value
    if (tab) await fetchFilterDishes(tab, true)
  }

  /** 首页筛选：按选中品类/标签拉取菜品列表（真实品类 categoryId 优先；tag 兼容旧用法），复用现有分页 */
  async function fetchFilterDishes(tab: FilterTab, reset = false) {
    const seq = ++filterFetchSeq
    if (reset) {
      filterList.value = []
      filterPage.value = 1
      filterFinished.value = false
      // 新一次查询开始：先清上次失败态（成功后本就为 false；若本次失败会再置 true）
      filterError.value = false
    }
    filterTab.value = tab
    try {
      const pageSize = 10
      let rows: Dish[] = []
      /** 首页排序（问题一）：映射为后端既有 sortBy/sortOrder 查询参数 */
      const s = sortParamsFor(homeSortBy.value)
      if (tab.type === 'category' && tab.categoryId != null) {
        const res = await dishApi.searchDishesPage({ categoryId: tab.categoryId, page: filterPage.value, pageSize, sortBy: s.sortBy, sortOrder: s.sortOrder, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max })
        // 仅写回距离供卡片「距你」展示，顺序由后端按排序项决定（loc-hint 提示开启定位才有意义）
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else if (tab.type === 'tag' && tab.payload) {
        const res = await dishApi.searchDishesPage({ tag: tab.payload, page: filterPage.value, pageSize, sortBy: s.sortBy, sortOrder: s.sortOrder, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max })
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else if (tab.type === 'canteen' && tab.canteenId != null) {
        // 按食堂过滤：canteenId → 后端 /dishes?canteenId=，顺序按当前排序项
        const res = await dishApi.searchDishesPage({ canteenId: tab.canteenId, page: filterPage.value, pageSize, sortBy: s.sortBy, sortOrder: s.sortOrder, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max })
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else {
        // 默认流：按当前排序项取分页（非距离排序沿用本地距离升序的历史兜底）
        const res = await dishApi.getHotDishesPage(filterPage.value, pageSize, filterPrice.value)
        rows = withLocalDistance(res.list, homeSortBy.value === 'distance')
        filterTotal.value = res.total
      }
      // 距离最近：后端无法按客户端坐标排序，加载后在前端重排
      if (homeSortBy.value === 'distance' && tab.type !== 'recommend') rows = sortByDistance(rows)
      // 过期响应（期间又切换了品类）直接丢弃，不覆盖新品类列表
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
  }

  /** 首页筛选触底加载更多 */
  async function loadMoreFilterDishes(): Promise<boolean> {
    const tab = filterTab.value
    if (!tab || filterLoadingMore.value || filterFinished.value) return false
    // 与 fetchFilterDishes 共用 filterFetchSeq：切换品类会使其自增，使在途的旧品类第 2 页结果失效，
    // 避免「切品类时旧品类第 2 页晚到 concat 进新品类列表」的竞态（P0 修复）
    const seq = ++filterFetchSeq
    filterLoadingMore.value = true
    filterPage.value += 1
    try {
      const pageSize = 10
      let rows: Dish[] = []
      /** 翻页沿用首页当前排序项（问题一） */
      const s = sortParamsFor(homeSortBy.value)
      if (tab.type === 'category' && tab.categoryId != null) {
        const res = await dishApi.searchDishesPage({ categoryId: tab.categoryId, page: filterPage.value, pageSize, sortBy: s.sortBy, sortOrder: s.sortOrder, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max })
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else if (tab.type === 'tag' && tab.payload) {
        const res = await dishApi.searchDishesPage({ tag: tab.payload, page: filterPage.value, pageSize, sortBy: s.sortBy, sortOrder: s.sortOrder, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max })
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else if (tab.type === 'canteen' && tab.canteenId != null) {
        const res = await dishApi.searchDishesPage({ canteenId: tab.canteenId, page: filterPage.value, pageSize, sortBy: s.sortBy, sortOrder: s.sortOrder, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max })
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else {
        const res = await dishApi.getHotDishesPage(filterPage.value, pageSize, filterPrice.value)
        rows = withLocalDistance(res.list, homeSortBy.value === 'distance')
        filterTotal.value = res.total
      }
      // 距离最近：本页按客户端距离升序，保证翻页后整体仍单调
      if (homeSortBy.value === 'distance' && tab.type !== 'recommend') rows = sortByDistance(rows)
      // 过期响应（期间又切换了品类）丢弃，不混入新品类列表
      if (seq !== filterFetchSeq) {
        filterPage.value -= 1
        return false
      }
      filterList.value = filterList.value.concat(rows)
      // 分页结束判据基于「本页返回条数 < pageSize」（见 fetchFilterDishes 说明）
      if (rows.length < pageSize) filterFinished.value = true
      return rows.length > 0
    } catch (e) {
      console.error('加载更多筛选菜品失败', e)
      filterPage.value -= 1
      return false
    } finally {
      filterLoadingMore.value = false
    }
  }

  /** 定位晚于首屏列表到达后，重算已加载菜品的本地距离（不重拉后端）：
   * - 仅刷新 filterList 中每个 Dish.distance（Haversine 复用 withLocalDistance 的距离写回逻辑）；
   * - 若当前排序为「距离最近」，则按新距离重排，使卡片顺序即时更新。 */
  function refreshLocalDistance() {
    if (filterList.value.length === 0) return
    const locStore = useLocationStore()
    const loc = locStore.location || CAMPUS_CENTER
    for (const d of filterList.value) {
      const dishLoc =
        typeof d.latitude === 'number' && typeof d.longitude === 'number'
          ? { lat: d.latitude, lng: d.longitude }
          : CAMPUS_CENTER
      d.distance = haversineMeters(loc, dishLoc)
    }
    if (homeSortBy.value === 'distance') {
      filterList.value = sortByDistance(filterList.value)
    }
  }

  return {
    dishList, currentDish, recommendList, guessList, reviewList, stallDishes,
    canteenList, newDishes, promotionDishes,
    hotSearchList, risingDishes, reviewTotal, reviewSort, reviewsDirty,
    loading, navParams,
    categories,
    filterTab, filterList, filterTotal, filterPage, filterLoadingMore, filterFinished, filterPrice, filterError,
    homeSortBy, setHomeSort, setHomePrice,
    fetchRecommend, fetchGuess,
    fetchCategories, fetchCanteens, search, searchPage, fetchDetail, resetDishDetail, resetUserScopedData, fetchReviews, fetchStallDishes,
    fetchNewDishes, fetchPromotionDishes, fetchHotSearch, fetchRising,
    fetchFilterDishes, loadMoreFilterDishes, refreshLocalDistance,
    withLocalDistance,
  }
})
