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

  // 顶层不再裸发请求（对齐 userStore）：未登录（无 token）时跳过，
  // 避免 http 拦截层 401 清 token 跳登录的副作用；有 token 时兜底加载并吞掉拒绝。
  if (typeof localStorage !== 'undefined' && localStorage.getItem('token')) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, activeList, add, update, remove }
})
