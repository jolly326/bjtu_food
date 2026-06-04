import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Review } from '@/types'
import { reviewApi } from '@/api'

export const useReviewStore = defineStore('review', () => {
  const list = ref<Review[]>([])

  async function loadAll() { list.value = await reviewApi.getAll() }
  async function add(data: Omit<Review, 'id' | 'created_at' | 'updated_at'>) { await reviewApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Review>) { await reviewApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await reviewApi.deleteById(id); await loadAll() }

  loadAll()
  return { list, add, update, remove }
})
