import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Dish } from '@/types/dish'
import * as favoriteApi from '@/api/favorite'

export const useFavoriteStore = defineStore('favorite', () => {
  const favoriteList = ref<Dish[]>([])
  const loading = ref(false)

  async function fetchFavorites() {
    loading.value = true
    try {
      favoriteList.value = await favoriteApi.getFavoriteList()
    } catch (e: any) {
      console.error('加载收藏失败', e)
    } finally {
      loading.value = false
    }
  }

  async function addFavorite(dishId: number) {
    try {
      await favoriteApi.addFavorite(dishId)
      // toggle 成功后重新拉取全量列表以获取最新顺序和数据
      await fetchFavorites()
    } catch (e: any) {
      console.error('收藏失败', e)
    }
  }

  async function removeFavorite(dishId: number) {
    try {
      await favoriteApi.removeFavorite(dishId)
      favoriteList.value = favoriteList.value.filter(d => d.id !== dishId)
    } catch (e: any) {
      console.error('取消收藏失败', e)
    }
  }

  const isFavorited = (dishId: number) => favoriteList.value.some(d => d.id === dishId)

  return { favoriteList, loading, fetchFavorites, addFavorite, removeFavorite, isFavorited }
})
