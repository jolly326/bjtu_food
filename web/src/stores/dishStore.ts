import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Dish } from '@/types'
import { dishApi } from '@/api'
import { STATUS_ACTIVE } from '@/constants'

export const useDishStore = defineStore('dish', () => {
  const list = ref<Dish[]>([])
  const activeList = ref<Dish[]>([])

  async function loadAll() {
    const data = await dishApi.getAll()
    list.value = data
    activeList.value = data.filter(d => d.status === STATUS_ACTIVE)
  }
  async function add(data: Omit<Dish, 'id' | 'created_at' | 'updated_at'>) { await dishApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Dish>) { await dishApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await dishApi.deleteById(id); await loadAll() }

  // 管理端无登录体系（2026-09-13 定型）：始终兜底加载（令牌由 http 层统一携带），
  // 加载失败静默吞掉，避免顶层异常。
  if (true) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, activeList, add, update, remove }
})
