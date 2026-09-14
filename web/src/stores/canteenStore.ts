import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Canteen } from '@/types'
import { canteenApi } from '@/api'

/**
 * 食堂 store（2026-09-14 Q-113/Q-115；2026-09-15 §7.23 第 1 条）：食堂是**菜品筛选属性字典**，非业务实体。
 * - 生命周期只有「按名 upsert（随菜品）/ 改名」，**无删除、无独立新增端点**；
 * - **不在 setup 顶层自动加载（WEB-02）**：由需要的页面（菜品列表筛选等）显式调用 loadAll()，
 *   避免任一 store 实例化即发请求、造成进页连发全量重复请求。
 */
export const useCanteenStore = defineStore('canteen', () => {
  const list = ref<Canteen[]>([])

  async function loadAll() {
    list.value = await canteenApi.getAll()
  }
  async function update(id: number, data: Partial<Canteen>) { await canteenApi.updateById(id, data); await loadAll() }

  return { list, loadAll, update }
})
