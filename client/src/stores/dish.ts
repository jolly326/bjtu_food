import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Dish, DishDetail, DishQuery, HotSearch } from '@/types/dish'
import type { Review, ReviewSubmit, ReviewSort } from '@/types/review'
import type { BannerItem } from '@/types/banner'
import type { CanteenInfo } from '@/types/canteen'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import * as canteenApi from '@/api/canteen'
import * as momentApi from '@/api/moment'
import { getRecommendDishes } from '@/api/recommend'
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
  const loading = ref(false)
  const navParams = { stallName: '', canteen: '' }
  const canteenList = ref<CanteenInfo[]>([])
  const newDishes = ref<Dish[]>([])
  const promotionDishes = ref<Dish[]>([])

  /** 首页筛选 Bar：单级横滑（推荐 / 美食类型），选中居中，切换即换内容 */
  const filterTab = ref<FilterTab | null>(null)
  const filterList = ref<Dish[]>([])
  const filterTotal = ref(0)
  const filterPage = ref(1)
  const filterLoadingMore = ref(false)
  const filterFinished = ref(false)

  /** task-02 榜单数据 */
  const hotSearchList = ref<HotSearch[]>([])
  const risingDishes = ref<Dish[]>([])

  /** task-03 评价分页 */
  const reviewTotal = ref(0)
  const reviewSort = ref<ReviewSort>('latest')
  const reviewOnlyImage = ref(false)

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
    loading.value = true
    try {
      recommendList.value = await dishApi.getRecommendList()
    } catch (e: any) {
      console.error('[store] fetchRecommend failed', e)
      recommendList.value = []
    } finally {
      loading.value = false
    }
  }

  /** 猜你喜欢：GET /dishes/recommend，未登录降级纯热度；excludeIds 去重 */
  async function fetchGuess(excludeIds: number[] = []) {
    loading.value = true
    try {
      const res = await getRecommendDishes({ excludeIds, pageSize: 10 })
      guessList.value = res.list
    } catch (e: any) {
      console.error('[store] fetchGuess failed', e)
      guessList.value = []
    } finally {
      loading.value = false
    }
  }

  async function search(query: DishQuery): Promise<Dish[]> {
    loading.value = true
    try {
      const list = await dishApi.searchDishes(query)
      dishList.value = list
      return list
    } catch (e: any) {
      console.error('搜索失败', e)
      dishList.value = []
      return []
    } finally {
      loading.value = false
    }
  }

  /** task-02 多维筛选结果页：返回分页结果（list + total），供无限加载 */
  async function searchPage(query: DishQuery): Promise<{ list: Dish[]; total: number }> {
    loading.value = true
    try {
      const res = await dishApi.searchDishesPage(query)
      dishList.value = res.list
      return res
    } catch (e: any) {
      console.error('搜索失败', e)
      dishList.value = []
      return { list: [], total: 0 }
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id: number) {
    loading.value = true
    try {
      currentDish.value = await dishApi.getDishDetail(id)
    } catch (e: any) {
      console.error('加载菜品详情失败', e)
      currentDish.value = null
    } finally {
      loading.value = false
    }
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
    loading.value = true
    try {
      const res = await reviewApi.getReviewsByDish(dishId, { sort, isWithImage, page, pageSize })
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
    } finally {
      loading.value = false
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
   * - 用户已授权定位：用真实坐标算距离，并按距离升序排序；
   * - 未授权 / 无法获取（如 H5 预览）：回退到 CAMPUS_CENTER，距离字段始终有值（排序仍按后端热度）。
   * 用户位置不出本机，服务器不算距离。
   */
  function withLocalDistance(list: Dish[]): Dish[] {
    const locStore = useLocationStore()
    const realLoc = locStore.location
    const loc = realLoc || CAMPUS_CENTER
    const decorated = list.map((d) => {
      if (typeof d.latitude === 'number' && typeof d.longitude === 'number') {
        d.distance = haversineMeters(loc, { lat: d.latitude, lng: d.longitude })
      }
      return d
    })
    if (realLoc) {
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
    loading.value = true
    try {
      stallDishes.value = await dishApi.getStallDishes(stallId)
    } catch (e: any) {
      console.error('加载档口菜品失败', e)
      stallDishes.value = []
    } finally {
      loading.value = false
    }
  }

  /** 首页筛选 Bar：按选中 tab 拉取菜品列表（推荐/美食类型），复用现有分页与距离排序 */
  async function fetchFilterDishes(tab: FilterTab, reset = false) {
    if (reset) {
      filterList.value = []
      filterPage.value = 1
      filterFinished.value = false
    }
    filterTab.value = tab
    try {
      const pageSize = 10
      let rows: Dish[] = []
      if (tab.type === 'tag' && tab.payload) {
        const res = await dishApi.searchDishesPage({ tag: tab.payload, page: filterPage.value, pageSize })
        rows = res.list
        filterTotal.value = res.total
      } else {
        // recommend：热度分页 + 本地距离升序（前期个性化未实现，回落距离/热度兜底）
        const res = await dishApi.getHotDishesPage(filterPage.value, pageSize)
        rows = withLocalDistance(res.list)
        filterTotal.value = res.total
      }
      if (reset) {
        filterList.value = rows
      } else {
        filterList.value = filterList.value.concat(rows)
      }
      // 分页结束判据基于「本页返回条数 < pageSize」，避免 recommend 本地排序后 total 语义不一致导致误判到底
      if (rows.length < pageSize) filterFinished.value = true
    } catch (e: any) {
      console.error('加载筛选菜品失败', e)
    }
  }

  /** 首页筛选 Bar 触底加载更多 */
  async function loadMoreFilterDishes(): Promise<boolean> {
    const tab = filterTab.value
    if (!tab || filterLoadingMore.value || filterFinished.value) return false
    filterLoadingMore.value = true
    filterPage.value += 1
    try {
      const pageSize = 10
      let rows: Dish[] = []
      if (tab.type === 'tag' && tab.payload) {
        const res = await dishApi.searchDishesPage({ tag: tab.payload, page: filterPage.value, pageSize })
        rows = res.list
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
    hotSearchList, risingDishes, reviewTotal, reviewSort, reviewOnlyImage, relatedMoments,
    loading, navParams,
    filterTab, filterList, filterTotal, filterPage, filterLoadingMore, filterFinished,
    fetchRecommend, fetchGuess, fetchHomeBanners, fetchCanteenImages,
    fetchCanteens, search, searchPage, fetchDetail, fetchReviews, submitReview, fetchStallDishes,
    fetchNewDishes, fetchPromotionDishes, fetchHotSearch, fetchRising,
    fetchRelatedMoments,
    fetchFilterDishes, loadMoreFilterDishes,
  }
})
