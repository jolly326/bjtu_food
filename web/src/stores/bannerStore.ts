import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Banner } from '@/types'
import { bannerApi } from '@/api'
import { STATUS_ACTIVE } from '@/constants'

export const useBannerStore = defineStore('banner', () => {
  const list = ref<Banner[]>([])

  async function loadAll() { list.value = await bannerApi.getAll() }

  const activeList = computed(() => list.value.filter(b => b.status === STATUS_ACTIVE))
  const sortedList = computed(() => [...list.value].sort((a, b) => a.sort_order - b.sort_order))
  const maxSortOrder = computed(() => Math.max(...list.value.map(b => b.sort_order), 0))

  async function add(data: Omit<Banner, 'id' | 'created_at' | 'updated_at'>) { await bannerApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Banner>) { await bannerApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await bannerApi.deleteById(id); await loadAll() }

  // 顶层不再裸发请求（对齐 userStore）：未登录（无 token）时跳过，
  // 避免 http 拦截层 401 清 token 跳登录的副作用；有 token 时兜底加载并吞掉拒绝。
  if (typeof localStorage !== 'undefined' && localStorage.getItem('token')) {
    loadAll().catch(() => {})
  }
  return { list, loadAll, activeList, sortedList, maxSortOrder, add, update, remove }
})
