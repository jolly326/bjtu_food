import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Canteen } from '@/types'
import { canteenApi } from '@/api'
import { STATUS_ACTIVE } from '@/constants'

export const useCanteenStore = defineStore('canteen', () => {
  const list = ref<Canteen[]>([])
  const activeList = ref<Canteen[]>([])

  async function loadAll() {
    const data = await canteenApi.getAll()
    list.value = data
    activeList.value = data.filter(c => c.status === STATUS_ACTIVE)
  }
  async function add(data: Omit<Canteen, 'id' | 'created_at' | 'updated_at'>) { await canteenApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Canteen>) { await canteenApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await canteenApi.deleteById(id); await loadAll() }

  // 管理端无登录体系（2026-09-13 定型）：始终兜底加载（令牌由 http 层统一携带），
  // 加载失败静默吞掉，避免顶层异常。
  if (true) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, activeList, add, update, remove }
})
