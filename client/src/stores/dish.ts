import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Dish, DishDetail, DishQuery, HotSearch } from '@/types/dish'
import type { Review, ReviewSubmit, ReviewSort } from '@/types/review'
import type { BannerItem } from '@/types/banner'
import type { CanteenInfo } from '@/types/canteen'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import * as canteenApi from '@/api/canteen'
import * as momentApi from '@/api/moment'
import { getRecommendDishes } from '@/api/recommend'
import { getCategories, type CategoryItem } from '@/api/category'
import { useLocationStore } from '@/stores/location'
import { haversineMeters, CAMPUS_CENTER } from '@/utils/location'
import type { FilterTab } from '@/components/filter-tab'

export const useDishStore = defineStore('dish', () => {
  const dishList = ref<Dish[]>([])
  const currentDish = ref<DishDetail | null>(null)
  const recommendList = ref<Dish[]>([])
  const guessList = ref<Dish[]>([])
  const reviewList = ref<Review[]>([])
  const stallDishes = ref<Dish[]>([])
  const homeBanners = ref<BannerItem[]>([])
  const canteenImageMap = ref<Record<string, string>>({})
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
  const filterLoadingMore = ref(false)
  const filterFinished = ref(false)
  /** 首页筛选流首拉/切换失败标记：HomeFeed 据此展示「加载失败 + 重试」，避免与广播/万能区错误态割裂 */
  const filterLoadFailed = ref(false)

  /** 首页品类滚轮数据源（后端 category 表 enabled 品类，按 sortOrder 升序） */
  const categories = ref<CategoryItem[]>([])

  /** 拉取品类列表（首页品类滚轮数据源；失败回退空数组） */
  async function fetchCategories(): Promise<CategoryItem[]> {
    try {
      categories.value = await getCategories()
    } catch (e: any) {
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
  const reviewOnlyImage = ref(false)
  /** 评价脏标记：写评价/回复/删除成功后置 true，onShow 据此决定是否重拉，避免每次返回都发请求（#8） */
  const reviewsDirty = ref(false)

  /** task-03 关联动态（二期占位，一期为空） */
  const relatedMoments = ref<any[]>([])

  async function fetchCanteens() {
    try {
      canteenList.value = await canteenApi.getCanteenList()
    } catch (e: any) {
      console.error('加载食堂列表失败', e)
      canteenList.value = []
    }
  }

  async function fetchHomeBanners() {
    try {
      homeBanners.value = await canteenApi.getHomeBanners()
    } catch (e: any) {
      console.error('加载轮播图失败', e)
      homeBanners.value = []
    }
  }

  async function fetchCanteenImages() {
    try {
      canteenImageMap.value = await canteenApi.getCanteenImages()
    } catch (e: any) {
      console.error('加载食堂背景图失败', e)
      canteenImageMap.value = {}
    }
  }

  async function fetchRecommend() {
    return withLoading('fetchRecommend', async () => {
      recommendList.value = await dishApi.getRecommendList()
    }).catch((e: any) => {
      console.error('[store] fetchRecommend failed', e)
      recommendList.value = []
    })
  }

  /** 猜你喜欢：GET /dishes/recommend，未登录降级纯热度；excludeIds 去重 */
  async function fetchGuess(excludeIds: number[] = []) {
    return withLoading('fetchGuess', async () => {
      const res = await getRecommendDishes({ excludeIds, pageSize: 10 })
      guessList.value = res.list
    }).catch((e: any) => {
      console.error('[store] fetchGuess failed', e)
      guessList.value = []
    })
  }

  async function search(query: DishQuery): Promise<Dish[]> {
    try {
      const list = await withLoading('search', async () => await dishApi.searchDishes(query))
      dishList.value = list
      return list
    } catch (e: any) {
      console.error('搜索失败', e)
      dishList.value = []
      return []
    }
  }

  /** task-02 多维筛选结果页：返回分页结果（list + total），供无限加载 */
  async function searchPage(query: DishQuery): Promise<{ list: Dish[]; total: number }> {
    try {
      const res = await withLoading('searchPage', async () => await dishApi.searchDishesPage(query))
      dishList.value = res.list
      return res
    } catch (e: any) {
      console.error('搜索失败', e)
      dishList.value = []
      return { list: [], total: 0 }
    }
  }

  async function fetchDetail(id: number) {
    return withLoading('fetchDetail', async () => {
      currentDish.value = await dishApi.getDishDetail(id)
    }).catch((e: any) => {
      console.error('加载菜品详情失败', e)
      currentDish.value = null
    })
  }

  /** 进入新菜品前清空旧详情与评价态，避免闪现上一道菜（store 全局状态残留）。统一走 action 而非外部直接写 ref。 */
  function resetDishDetail() {
    currentDish.value = null
    reviewList.value = []
    reviewTotal.value = 0
  }

  /**
   * task-03 评价区重做：分页 + 排序 + 晒图过滤。
   * sort: latest|useful；isWithImage 晒图过滤。返回结果写入 reviewList/reviewTotal。
   */
  async function fetchReviews(
    dishId: number,
    options?: { sort?: ReviewSort; isWithImage?: boolean; page?: number; pageSize?: number; append?: boolean },
  ): Promise<{ list: Review[]; total: number }> {
    const sort = options?.sort ?? reviewSort.value
    const isWithImage = options?.isWithImage ?? reviewOnlyImage.value
    reviewSort.value = sort
    reviewOnlyImage.value = isWithImage
    const page = options?.page ?? 1
    const pageSize = options?.pageSize ?? 50
    try {
      const res = await withLoading('fetchReviews', async () =>
        await reviewApi.getReviewsByDish(dishId, { sort, isWithImage, page, pageSize }))
      if (options?.append) {
        reviewList.value = [...reviewList.value, ...res.list]
      } else {
        reviewList.value = res.list
      }
      reviewTotal.value = res.total
      return res
    } catch (e: any) {
      console.error('加载评价失败', e)
      if (!options?.append) {
        reviewList.value = []
        reviewTotal.value = 0
      }
      return { list: reviewList.value, total: reviewTotal.value }
    }
  }

  async function submitReview(data: ReviewSubmit) {
    await reviewApi.submitReview(data)
  }

  async function fetchNewDishes(): Promise<Dish[]> {
    try {
      const data = await dishApi.getNewDishes()
      newDishes.value = data
      return data
    } catch (e: any) {
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
    } catch (e: any) {
      console.error('加载活动菜品失败', e)
      promotionDishes.value = []
      return []
    }
  }

  /** task-02 热搜 TOP10（派生热度词条） */
  async function fetchHotSearch() {
    try {
      hotSearchList.value = await dishApi.getHotSearch()
    } catch (e: any) {
      console.error('加载热搜失败', e)
      hotSearchList.value = []
    }
  }

  /** task-02 新晋黑马 */
  async function fetchRising() {
    try {
      risingDishes.value = await dishApi.getRisingDishes()
    } catch (e: any) {
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

  /** task-12.6 关联动态聚合：GET /moments?dishId= */
  async function fetchRelatedMoments(dishId: number) {
    try {
      const { list } = await momentApi.getMoments({ dishId, pageSize: 10 })
      relatedMoments.value = list
    } catch (e: any) {
      console.error('加载关联动态失败', e)
      relatedMoments.value = []
    }
  }

  async function fetchStallDishes(stallId: number) {
    return withLoading('fetchStallDishes', async () => {
      stallDishes.value = await dishApi.getStallDishes(stallId)
    }).catch((e: any) => {
      console.error('加载档口菜品失败', e)
      stallDishes.value = []
    })
  }

  /** 筛选请求序号：快速切换品类时丢弃过期响应，避免旧请求晚到覆盖新品类（P0 竞态修复） */
  let filterFetchSeq = 0

  /** 首页筛选：按选中品类/标签拉取菜品列表（真实品类 categoryId 优先；tag 兼容旧用法），复用现有分页 */
  async function fetchFilterDishes(tab: FilterTab, reset = false) {
    const seq = ++filterFetchSeq
    if (reset) {
      filterList.value = []
      filterPage.value = 1
      filterFinished.value = false
    }
    filterTab.value = tab
    filterLoadFailed.value = false
    try {
      const pageSize = 10
      let rows: Dish[] = []
      if (tab.type === 'category' && tab.categoryId != null) {
        const res = await dishApi.searchDishesPage({ categoryId: tab.categoryId, page: filterPage.value, pageSize, sortBy: 'heat', sortOrder: 'desc' })
        // 保持后端热度序，仅写回距离供卡片「距你」展示（loc-hint 提示开启定位才有意义）
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else if (tab.type === 'tag' && tab.payload) {
        const res = await dishApi.searchDishesPage({ tag: tab.payload, page: filterPage.value, pageSize })
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else {
        // recommend：热度分页 + 本地距离升序（前期个性化未实现，回落距离/热度兜底）
        const res = await dishApi.getHotDishesPage(filterPage.value, pageSize)
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
      }
      // 过期响应（期间又切换了品类）直接丢弃，不覆盖新品类列表
      if (seq !== filterFetchSeq) return
      if (reset) {
        filterList.value = rows
      } else {
        filterList.value = filterList.value.concat(rows)
      }
      // 分页结束判据基于「本页返回条数 < pageSize」，避免 recommend 本地排序后 total 语义不一致导致误判到底
      if (rows.length < pageSize) filterFinished.value = true
    } catch (e: any) {
      // 过期请求失败不再置失败态（新请求状态为准）
      if (seq !== filterFetchSeq) return
      console.error('加载筛选菜品失败', e)
      filterLoadFailed.value = true
    }
  }

  /** 首页筛选触底加载更多 */
  async function loadMoreFilterDishes(): Promise<boolean> {
    const tab = filterTab.value
    if (!tab || filterLoadingMore.value || filterFinished.value) return false
    filterLoadingMore.value = true
    filterPage.value += 1
    try {
      const pageSize = 10
      let rows: Dish[] = []
      if (tab.type === 'category' && tab.categoryId != null) {
        const res = await dishApi.searchDishesPage({ categoryId: tab.categoryId, page: filterPage.value, pageSize, sortBy: 'heat', sortOrder: 'desc' })
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else if (tab.type === 'tag' && tab.payload) {
        const res = await dishApi.searchDishesPage({ tag: tab.payload, page: filterPage.value, pageSize })
        rows = withLocalDistance(res.list, false)
        filterTotal.value = res.total
      } else {
        const res = await dishApi.getHotDishesPage(filterPage.value, pageSize)
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
      }
      filterList.value = filterList.value.concat(rows)
      // 分页结束判据基于「本页返回条数 < pageSize」（见 fetchFilterDishes 说明）
      if (rows.length < pageSize) filterFinished.value = true
      return rows.length > 0
    } catch (e: any) {
      console.error('加载更多筛选菜品失败', e)
      filterPage.value -= 1
      return false
    } finally {
      filterLoadingMore.value = false
    }
  }

  return {
    dishList, currentDish, recommendList, guessList, reviewList, stallDishes,
    homeBanners, canteenImageMap, canteenList, newDishes, promotionDishes,
    hotSearchList, risingDishes, reviewTotal, reviewSort, reviewOnlyImage, relatedMoments, reviewsDirty,
    loading, navParams,
    categories,
    filterTab, filterList, filterTotal, filterPage, filterLoadingMore, filterFinished, filterLoadFailed,
    fetchRecommend, fetchGuess, fetchHomeBanners, fetchCanteenImages,
    fetchCategories, fetchCanteens, search, searchPage, fetchDetail, resetDishDetail, fetchReviews, submitReview, fetchStallDishes,
    fetchNewDishes, fetchPromotionDishes, fetchHotSearch, fetchRising,
    fetchRelatedMoments,
    fetchFilterDishes, loadMoreFilterDishes,
    withLocalDistance,
  }
})
