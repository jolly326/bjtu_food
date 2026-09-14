import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Stall } from '@/types'
import { stallApi } from '@/api'

/**
 * 档口 store（2026-09-14 Q-113/Q-115）：档口是**菜品筛选属性字典**，非业务实体。
 * - 生命周期只有「新增 / 改名」，**无删除**（Q-115：后端 DELETE 端点下线）；
 * - 不再维护 activeList（营业/停业状态已从属性字典中移除）。
 */
export const useStallStore = defineStore('stall', () => {
  const list = ref<Stall[]>([])

  async function loadAll() {
    list.value = await stallApi.getAll()
  }
  async function add(data: Omit<Stall, 'id' | 'created_at' | 'updated_at'>) { await stallApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Stall>) { await stallApi.updateById(id, data); await loadAll() }

  // 管理端无登录体系（2026-09-13 定型）：始终兜底加载（令牌由 http 层统一携带），
  // 加载失败静默吞掉，避免顶层异常。
  if (true) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, add, update }
})
