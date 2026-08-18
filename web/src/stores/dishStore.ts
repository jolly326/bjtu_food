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

  // 顶层不再裸发请求（对齐 userStore）：未登录（无 token）时跳过，
  // 避免 http 拦截层 401 清 token 跳登录的副作用；有 token 时兜底加载并吞掉拒绝。
  if (typeof localStorage !== 'undefined' && localStorage.getItem('token')) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, activeList, add, update, remove }
})
