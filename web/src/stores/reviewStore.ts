import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Review } from '@/types'
import { reviewApi } from '@/api'

export const useReviewStore = defineStore('review', () => {
  const list = ref<Review[]>([])

  async function loadAll() { list.value = await reviewApi.getAll() }
  // 注意：评价由学生端提交，后台不提供 create（见 review.ts P3-W5）
  async function update(id: number, data: Partial<Review>) { await reviewApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await reviewApi.deleteById(id); await loadAll() }

  loadAll()
  return { list, update, remove }
})
