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
import { haversineMeters } from '@/utils/location'

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

  /** task-01 首页热门瀑布流（双列 + 无限加载） */
  const homeHotList = ref<Dish[]>([])
  const homeHotTotal = ref(0)
  const homeHotPage = ref(1)
  const homeHotLoadingMore = ref(false)
  const homeHotFinished = ref(false)

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

  async function search(query: DishQuery) {
    loading.value = true
    try {
      dishList.value = await dishApi.searchDishes(query)
    } catch (e: any) {
      console.error('搜索失败', e)
      dishList.value = []
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

  /** task-02 搜索联想（GET /dishes/suggest，混合菜品/档口/食堂） */
  async function fetchSuggestions(keyword: string): Promise<import('@/types/dish').Suggestion[]> {
    try {
      return await dishApi.getSuggestions(keyword)
    } catch (e: any) {
      console.error('搜索联想失败', e)
      return []
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

  /** task-01 首页热门瀑布流：首屏 + 重置分页。
   *  距离（米）由前端基于 locationStore 用户坐标 + Haversine 本地计算写回 dish.distance，
   *  并按距离升序排序（仅在有定位时）；用户位置不出本机，服务器不再算距离。 */
  async function fetchHomeHot() {
    homeHotPage.value = 1
    homeHotFinished.value = false
    homeHotLoadingMore.value = false
    try {
      const res = await dishApi.getHotDishesPage(1)
      homeHotList.value = withLocalDistance(res.list)
      homeHotTotal.value = res.total
      if (homeHotList.value.length >= homeHotTotal.value) homeHotFinished.value = true
    } catch (e: any) {
      console.error('加载首页热门失败', e)
      homeHotList.value = []
      homeHotTotal.value = 0
    }
  }

  /** 基于 locationStore 用户坐标 + Haversine 本地写回每个菜品 distance（米），并按距离升序排序（有定位时） */
  function withLocalDistance(list: Dish[]): Dish[] {
    const loc = useLocationStore().location
    const decorated = list.map((d) => {
      if (loc && typeof d.latitude === 'number' && typeof d.longitude === 'number') {
        d.distance = haversineMeters(loc, { lat: d.latitude, lng: d.longitude })
      }
      return d
    })
    if (loc) {
      decorated.sort((a, b) => (a.distance ?? Number.MAX_SAFE_INTEGER) - (b.distance ?? Number.MAX_SAFE_INTEGER))
    }
    return decorated
  }

  /** task-01 首页热门瀑布流：触底追加（去重） */
  async function loadMoreHomeHot(): Promise<boolean> {
    if (homeHotLoadingMore.value || homeHotFinished.value) return false
    homeHotLoadingMore.value = true
    const nextPage = homeHotPage.value + 1
    try {
      const res = await dishApi.getHotDishesPage(nextPage)
      const existIds = new Set(homeHotList.value.map(d => d.id))
      const added = withLocalDistance(res.list.filter(d => !existIds.has(d.id)))
      homeHotList.value = [...homeHotList.value, ...added]
      homeHotPage.value = nextPage
      homeHotTotal.value = res.total
      if (homeHotList.value.length >= homeHotTotal.value) homeHotFinished.value = true
      return added.length > 0
    } catch (e: any) {
      console.error('加载首页热门更多失败', e)
      return false
    } finally {
      homeHotLoadingMore.value = false
    }
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

  return {
    dishList, currentDish, recommendList, guessList, reviewList, stallDishes,
    homeBanners, canteenImageMap, canteenList, newDishes, promotionDishes,
    homeHotList, homeHotTotal, homeHotPage, homeHotLoadingMore, homeHotFinished,
    hotSearchList, risingDishes, reviewTotal, reviewSort, reviewOnlyImage, relatedMoments,
    loading, navParams,
    fetchRecommend, fetchGuess, fetchHomeBanners, fetchCanteenImages,
    fetchCanteens, search, searchPage, fetchDetail, fetchReviews, submitReview, fetchStallDishes,
    fetchNewDishes, fetchPromotionDishes, fetchHotSearch, fetchRising, fetchSuggestions,
    fetchHomeHot, loadMoreHomeHot, fetchRelatedMoments,
  }
})
