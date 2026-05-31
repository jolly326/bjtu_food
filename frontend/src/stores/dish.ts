import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Dish, DishDetail, DishQuery } from '@/types/dish'
import type { Review, ReviewSubmit } from '@/types/review'
import type { BannerItem, CanteenInfo } from '@/types/canteen'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import * as canteenApi from '@/api/canteen'

export const useDishStore = defineStore('dish', () => {
  const dishList = ref<Dish[]>([])
  const currentDish = ref<DishDetail | null>(null)
  const recommendList = ref<Dish[]>([])
  const reviewList = ref<Review[]>([])
  const stallDishes = ref<Dish[]>([])
  const homeBanners = ref<BannerItem[]>([])
  const canteenImageMap = ref<Record<string, string>>({})
  const loading = ref(false)
  // 跨页面传参，导航前设置
  const navParams = { stallName: '', canteen: '' }
  const canteenList = ref<CanteenInfo[]>([])

  async function fetchCanteens() {
    try {
      canteenList.value = await canteenApi.getCanteenList()
    } catch (e: any) {
      console.error('加载食堂列表失败', e)
    }
  }

  async function fetchHomeBanners() {
    try {
      homeBanners.value = await canteenApi.getHomeBanners()
    } catch (e: any) {
      console.error('加载轮播图失败', e)
    }
  }

  async function fetchCanteenImages() {
    try {
      canteenImageMap.value = await canteenApi.getCanteenImages()
    } catch (e: any) {
      console.error('加载食堂背景图失败', e)
    }
  }

  async function fetchRecommend() {
    loading.value = true
    try {
      recommendList.value = await dishApi.getRecommendList()
    } catch (e: any) {
      console.error('[store] fetchRecommend失败', e)
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

  async function fetchReviews(dishId: number) {
    loading.value = true
    try {
      reviewList.value = await reviewApi.getReviewsByDish(dishId)
    } catch (e: any) {
      console.error('加载评价失败', e)
    } finally {
      loading.value = false
    }
  }

  async function submitReview(data: ReviewSubmit) {
    await reviewApi.submitReview(data)
  }

  async function fetchStallDishes(canteen: string, stallName: string) {
    loading.value = true
    try {
      stallDishes.value = await dishApi.getStallDishes(canteen, stallName)
    } catch (e: any) {
      console.error('加载档口菜品失败', e)
    } finally {
      loading.value = false
    }
  }

  return {
    dishList, currentDish, recommendList, reviewList, stallDishes,
    homeBanners, canteenImageMap, canteenList,
    loading, navParams,
    fetchRecommend, fetchHomeBanners, fetchCanteenImages,
    fetchCanteens, search, fetchDetail, fetchReviews, submitReview, fetchStallDishes,
  }
})
