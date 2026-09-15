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
  // 注（2026-09-15 取消人工复核）：原「安检复核（放行 / 驳回）」动作已删除，后台只做隐藏 / 显示 / 删除。

  // **不在 setup 顶层自动加载（WEB-02）**：由需要的页面（评价管理 / 菜品评论等）显式调用 loadAll()。
  return { list, loadAll, update, remove }
})
