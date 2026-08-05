import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Banner } from '@/types'
import { bannerApi } from '@/api'

export const useBannerStore = defineStore('banner', () => {
  const list = ref<Banner[]>([])

  async function loadAll() { list.value = await bannerApi.getAll() }

  const activeList = computed(() => list.value.filter(b => b.status === 'active'))
  const sortedList = computed(() => [...list.value].sort((a, b) => a.sort_order - b.sort_order))
  const maxSortOrder = computed(() => Math.max(...list.value.map(b => b.sort_order), 0))

  async function add(data: Omit<Banner, 'id' | 'created_at' | 'updated_at'>) { await bannerApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Banner>) { await bannerApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await bannerApi.deleteById(id); await loadAll() }

  loadAll()
  return { list, loadAll, activeList, sortedList, maxSortOrder, add, update, remove }
})
