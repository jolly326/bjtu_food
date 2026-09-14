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

  // **不在 setup 顶层自动加载（WEB-02）**：由需要的页面（评价审核/菜品评论等）显式调用 loadAll()。
  return { list, loadAll, update, updateSecState, remove }
})
