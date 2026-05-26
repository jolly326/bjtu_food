import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Dish, DishDetail, DishQuery } from '@/types/dish'
import type { Review, ReviewSubmit } from '@/types/review'
import type { CanteenInfo } from '@/stores/types'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'

export const useDishStore = defineStore('dish', () => {
  const dishList = ref<Dish[]>([])
  const currentDish = ref<DishDetail | null>(null)
  const recommendList = ref<Dish[]>([])
  const reviewList = ref<Review[]>([])
  const stallDishes = ref<Dish[]>([])
  const loading = ref(false)
  const error = ref('')
  // 跨页面传参（普通对象，通过手动触发导航前设置）
  const navParams = { stallName: '', canteen: '' }
  const canteenList = ref<CanteenInfo[]>([])

  async function fetchCanteens() {
    canteenList.value = await dishApi.getCanteenList()
  }

  async function fetchRecommend() {
    loading.value = true
    error.value = ''
    try {
      const list = await dishApi.getRecommendList()
      recommendList.value = list
      dishList.value = list
    } catch (e: any) {
      error.value = e.message || '加载推荐失败'
    } finally {
      loading.value = false
    }
  }

  async function search(query: DishQuery) {
    loading.value = true
    error.value = ''
    try {
      dishList.value = await dishApi.searchDishes(query)
    } catch (e: any) {
      error.value = e.message || '搜索失败'
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id: number) {
    loading.value = true
    error.value = ''
    try {
      currentDish.value = await dishApi.getDishDetail(id)
    } catch (e: any) {
      error.value = e.message || '加载菜品详情失败'
      currentDish.value = null
    } finally {
      loading.value = false
    }
  }

  async function fetchReviews(dishId: number) {
    try {
      reviewList.value = await reviewApi.getReviewsByDish(dishId)
    } catch (e: any) {
      console.error('加载评价失败', e)
    }
  }

  async function submitReview(data: ReviewSubmit) {
    try {
      await reviewApi.submitReview(data)
    } catch (e: any) {
      throw new Error(e.message || '提交评价失败')
    }
  }

  async function fetchStallDishes(canteen: string, stallName: string) {
    try {
      stallDishes.value = await dishApi.getStallDishes(canteen, stallName)
    } catch (e: any) {
      console.error('加载档口菜品失败', e)
    }
  }

  return {
    dishList, currentDish, recommendList, reviewList, stallDishes, canteenList,
    loading, error, navParams,
    fetchRecommend, fetchCanteens, search, fetchDetail, fetchReviews, submitReview, fetchStallDishes,
  }
})
