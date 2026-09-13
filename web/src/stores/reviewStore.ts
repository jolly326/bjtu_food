import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Review, SecAction } from '@/types'
import { reviewApi } from '@/api'

export const useReviewStore = defineStore('review', () => {
  const list = ref<Review[]>([])

  async function loadAll() { list.value = await reviewApi.getAll() }
  // 注意：评价由学生端提交，后台不提供 create（见 review.ts P3-W5）
  async function update(id: number, data: Partial<Review>) { await reviewApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await reviewApi.deleteById(id); await loadAll() }
  // 内容安检复核（放行/驳回）后重拉全量，保持列表 secState 即时刷新
  async function updateSecState(id: number, state: SecAction) { await reviewApi.updateSecState(id, state); await loadAll() }

  // 管理端无登录体系（2026-09-13 定型）：始终兜底加载（令牌由 http 层统一携带），
  // 加载失败静默吞掉，避免顶层异常。
  if (true) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, update, updateSecState, remove }
})
