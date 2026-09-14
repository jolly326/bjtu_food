import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Canteen } from '@/types'
import { canteenApi } from '@/api'

/**
 * 食堂 store（2026-09-14 Q-113/Q-115）：食堂是**菜品筛选属性字典**，非业务实体。
 * - 生命周期只有「新增 / 改名」，**无删除**（Q-115：后端 DELETE 端点下线）；
 * - 不再维护 activeList（营业/停业状态已从属性字典中移除）。
 */
export const useCanteenStore = defineStore('canteen', () => {
  const list = ref<Canteen[]>([])

  async function loadAll() {
    list.value = await canteenApi.getAll()
  }
  async function add(data: Omit<Canteen, 'id' | 'created_at' | 'updated_at'>) { await canteenApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Canteen>) { await canteenApi.updateById(id, data); await loadAll() }

  // 管理端无登录体系（2026-09-13 定型）：始终兜底加载（令牌由 http 层统一携带），
  // 加载失败静默吞掉，避免顶层异常。
  if (true) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, add, update }
})
