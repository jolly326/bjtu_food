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

  // 顶层不再裸发请求（对齐 userStore）：未登录（无 token）时跳过，
  // 避免 http 拦截层 401 清 token 跳登录的副作用；有 token 时兜底加载并吞掉拒绝。
  if (typeof localStorage !== 'undefined' && localStorage.getItem('token')) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, activeList, add, update, remove }
})
