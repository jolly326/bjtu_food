import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Stall } from '@/types'
import { stallApi } from '@/api'
import { STATUS_ACTIVE } from '@/constants'

export const useStallStore = defineStore('stall', () => {
  const list = ref<Stall[]>([])
  const activeList = ref<Stall[]>([])

  async function loadAll() {
    const data = await stallApi.getAll()
    list.value = data
    activeList.value = data.filter(s => s.status === STATUS_ACTIVE)
  }
  async function add(data: Omit<Stall, 'id' | 'created_at' | 'updated_at'>) { await stallApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Stall>) { await stallApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await stallApi.deleteById(id); await loadAll() }

  // 管理端无登录体系（2026-09-13 定型）：始终兜底加载（令牌由 http 层统一携带），
  // 加载失败静默吞掉，避免顶层异常。
  if (true) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, activeList, add, update, remove }
})
