import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Dish } from '@/types'
import { dishApi } from '@/api'
import type { DishSavePayload } from '@/api/dish'

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
  async function add(data: DishSavePayload) { await dishApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Dish>) { await dishApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await dishApi.deleteById(id); await loadAll() }

  // **不在 setup 顶层自动加载（WEB-02）**：由需要的页面（菜品列表/详情等）显式调用 loadAll()，
  // 避免 store 实例化即发全量请求、与其他页面加载叠加成「进页连发多轮全量」。
  return { list, loadAll, add, update, remove }
})
