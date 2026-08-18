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

  // 顶层不再裸发请求（对齐 userStore）：未登录（无 token）时跳过，
  // 避免 http 拦截层 401 清 token 跳登录的副作用；有 token 时兜底加载并吞掉拒绝。
  if (typeof localStorage !== 'undefined' && localStorage.getItem('token')) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, update, remove }
})
