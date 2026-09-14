import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Dish, DishDetail, DishQuery, HotSearch } from '@/types/dish'
import type { Review } from '@/types/review'
import type { CanteenInfo } from '@/types/canteen'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import * as canteenApi from '@/api/canteen'
import { useLocationStore } from '@/stores/location'
import { haversineMeters, CAMPUS_CENTER } from '@/utils/location'
import type { FilterTab } from '@/types/filter-tab'

export const useDishStore = defineStore('dish', () => {
  const currentDish = ref<DishDetail | null>(null)
  const reviewList = ref<Review[]>([])
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
  const filterTotal = ref(0)
  const filterPage = ref(1)
  /** 首页价格筛选区间（元，null/undefined 表示不限）；直接透传 api（api 层统一元→分），禁止二次换算/裸算 /100 */
  const filterPrice = ref<{ min?: number; max?: number }>({})
  /**
   * 首页辣度筛选（§7.18）：null = 全部/不限（不传 spiceLevel）；0=不辣 1=微辣 2=中辣 3=重辣。
   * 单选，与价格区间同层同生命周期（切换重置到第 1 页 + 复用 filterFetchSeq 竞态防护）。
   */
  const filterSpice = ref<number | null>(null)
  const filterLoadingMore = ref(false)
  const filterFinished = ref(false)
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
    reviewError.value = false
  }

  /**
   * 登录态变更（登出/换用户）时清理「用户态个性化数据」：
   * - reviewList 中各评价的 isUseful（有用标记是当前用户维度）
   * 防止 forceLogout 后上一用户的偏好数据残留串档（§5.x 登录态一致性）。
   */
  function resetUserScopedData() {
    for (const r of reviewList.value) {
      if (r.useful) r.useful = false
    }
  }

  /**
   * task-03 评价区重做：分页。
   * 排序由后端唯一决定（spec §7.14 第 2 条 / §7.18 第 3 条：公开列表默认按「有用数」置顶），
   * 端上不传 sort、不持有排序状态（PR-02「同一口径唯一权威方 = 后端」、§7.18 第 3 条「不提供排序切换」）。
   * 返回结果写入 reviewList/reviewTotal。
   * 竞态守卫（reviewFetchSeq，对齐 filterFetchSeq 模式）：触底 append 与进新菜品
   * 的 reset 交错时，过期响应直接丢弃不写入，防旧页数据 append 污染新列表；
   * 过期/失败的请求返回 null，调用方据其跳过分页推进。
   */
  async function fetchReviews(
    dishId: number,
    options?: { page?: number; pageSize?: number; append?: boolean },
  ): Promise<{ list: Review[]; total: number } | null> {
    const seq = ++reviewFetchSeq
    const page = options?.page ?? 1
    const pageSize = options?.pageSize ?? 50
    try {
      const res = await withLoading('fetchReviews', async () =>
        await reviewApi.getReviewsByDish(dishId, { page, pageSize }))
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
      return { list: reviewList.value, total: reviewTotal.value }
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

  /**
   * 基于坐标 + Haversine 本地写回每个菜品 distance（米），**只写回不排序**：
   * - 用户已授权定位：用真实坐标算距离；未授权 / 无法获取（如 H5 预览）：回退到 CAMPUS_CENTER，
   *   距离字段始终有值；
   * - 菜品坐标缺失（旧库 canteen 无坐标 / 后端返回 null）：回退 CAMPUS_CENTER 兜底计算，
   *   保证「距你 Xm」恒有值（语义：距校区中心），避免卡片整行不显示（P0 UI 缺漏）。
   * 列表顺序**一律由后端排序口径决定**（§7.17 第 2 条「定位仅用于展示距你 Xm，不改变排序口径」；
   * 端上不持有排序状态、不传 sortBy——PR-02），故本函数不再提供本地排序分支。
   * 用户位置不出本机，服务器不算距离。
   */
  function withLocalDistance(list: Dish[]): Dish[] {
    const locStore = useLocationStore()
    const loc = locStore.location || CAMPUS_CENTER
    return list.map((d) => {
      const dishLoc =
        typeof d.latitude === 'number' && typeof d.longitude === 'number'
          ? { lat: d.latitude, lng: d.longitude }
          : CAMPUS_CENTER // 菜品坐标缺失兜底：距校区中心
      d.distance = haversineMeters(loc, dishLoc)
      return d
    })
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
   * 首页辣度筛选（§7.18）：写回选中档位（null = 不限）并刷新当前筛选流——
   * 与 setHomePrice 完全同路径：reset=true 使 filterPage 归 1，
   * 竞态由 fetchFilterDishes 内既有 filterFetchSeq 序号守卫复用（不另写一套竞态逻辑）。
   */
  async function setHomeSpice(level: number | null) {
    if (filterSpice.value === level) return
    filterSpice.value = level
    const tab = filterTab.value
    if (tab) await fetchFilterDishes(tab, true)
  }

  /** 首页筛选：按选中食堂/标签拉取菜品列表，复用现有分页 */
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
      /** 辣度筛选（§7.18）：null = 不限，不传该查询参数 */
      const spice = filterSpice.value ?? undefined
      // 端上不传 sortBy/sortOrder：列表顺序唯一由后端排序口径决定（§7.17 第 2 条「热度优先」；PR-02）
      if (tab.type === 'tag' && tab.payload) {
        const res = await dishApi.searchDishesPage({ tag: tab.payload, page: filterPage.value, pageSize, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max, spiceLevel: spice })
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
      } else if (tab.type === 'canteen' && tab.canteenId != null) {
        // 按食堂过滤：canteenId → 后端 /dishes?canteenId=，顺序由后端决定
        const res = await dishApi.searchDishesPage({ canteenId: tab.canteenId, page: filterPage.value, pageSize, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max, spiceLevel: spice })
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
      } else {
        // 默认流：热度优先（后端口径）
        const res = await dishApi.getHotDishesPage(filterPage.value, pageSize, filterPrice.value, spice)
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
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
  }

  /** 首页筛选触底加载更多 */
  async function loadMoreFilterDishes(): Promise<boolean> {
    const tab = filterTab.value
    if (!tab || filterLoadingMore.value || filterFinished.value) return false
    // 与 fetchFilterDishes 共用 filterFetchSeq：切换筛选条件会使其自增，使在途的旧条件第 2 页结果失效，
    // 避免「切换条件时旧结果第 2 页晚到 concat 进新列表」的竞态（P0 修复）
    const seq = ++filterFetchSeq
    filterLoadingMore.value = true
    filterPage.value += 1
    try {
      const pageSize = 10
      let rows: Dish[] = []
      /** 辣度筛选（§7.18）：翻页沿用当前选中档位，null = 不限 */
      const spice = filterSpice.value ?? undefined
      // 与 fetchFilterDishes 一致：端上不传 sortBy，顺序由后端口径决定（PR-02）
      if (tab.type === 'tag' && tab.payload) {
        const res = await dishApi.searchDishesPage({ tag: tab.payload, page: filterPage.value, pageSize, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max, spiceLevel: spice })
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
      } else if (tab.type === 'canteen' && tab.canteenId != null) {
        const res = await dishApi.searchDishesPage({ canteenId: tab.canteenId, page: filterPage.value, pageSize, minPrice: filterPrice.value.min, maxPrice: filterPrice.value.max, spiceLevel: spice })
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
      } else {
        const res = await dishApi.getHotDishesPage(filterPage.value, pageSize, filterPrice.value, spice)
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
      }
      // 过期响应（期间又切换了筛选条件）丢弃，不混入新列表
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
   * 仅刷新 filterList 中每个 Dish.distance（Haversine 复用 withLocalDistance 的距离写回逻辑）；
   * 顺序**不动**——排序唯一由后端口径决定（§7.17 第 2 条：定位仅用于「距你 Xm」展示，不改变排序口径）。 */
  function refreshLocalDistance() {
    if (filterList.value.length === 0) return
    filterList.value = withLocalDistance(filterList.value)
  }

  return {
    currentDish, reviewList,
    canteenList,
    hotSearchList, reviewTotal, reviewError,
    loading,
    filterTab, filterList, filterTotal, filterPage, filterLoadingMore, filterFinished, filterPrice, filterSpice, filterError,
    setHomePrice, setHomeSpice,
    fetchCanteens, refreshCanteensIfStale, search, fetchDetail, resetDishDetail, resetUserScopedData, fetchReviews,
    fetchHotSearch,
    fetchFilterDishes, loadMoreFilterDishes, refreshLocalDistance,
    withLocalDistance,
  }
})
