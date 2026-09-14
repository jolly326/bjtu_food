import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Dish } from '@/types'
import { dishApi } from '@/api'

/**
 * 菜品 store。
 * `activeList` 已于 2026-09-14（P3-08）移除：唯一消费方是 adminStore 的 `activeDishes`（零消费死成员），
 * 删除后无任何页面/组件读取，避免多维护一份派生数据。
 */
export const useDishStore = defineStore('dish', () => {
  const list = ref<Dish[]>([])

  async function loadAll() {
    list.value = await dishApi.getAll()
  }
  async function add(data: Omit<Dish, 'id' | 'created_at' | 'updated_at'>) { await dishApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Dish>) { await dishApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await dishApi.deleteById(id); await loadAll() }

  // 管理端无登录体系（2026-09-13 定型）：始终兜底加载（令牌由 http 层统一携带），
  // 加载失败静默吞掉，避免顶层异常。
  if (true) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, add, update, remove }
})
