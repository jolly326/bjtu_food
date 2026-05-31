import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Review } from '@/types'
import { reviewApi } from '@/api'

export const useReviewStore = defineStore('review', () => {
  const list = ref<Review[]>([])

  function loadAll() { list.value = reviewApi.getAll() }
  function remove(id: number) { reviewApi.deleteById(id); loadAll() }

  loadAll()
  return { list, remove }
})
