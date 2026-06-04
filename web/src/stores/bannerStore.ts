import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Banner } from '@/types'
import { bannerApi } from '@/api'

export const useBannerStore = defineStore('banner', () => {
  const list = ref<Banner[]>([])
  const activeList = computed(() => list.value.filter(b => b.status === 'active'))
  /** 停用的自动沉底：先按 status（active 在前），再按 sort_order */
  const sortedList = computed(() =>
    [...list.value].sort((a, b) => {
      if (a.status !== b.status) return a.status === 'active' ? -1 : 1
      return a.sort_order - b.sort_order
    })
  )
  const maxSortOrder = computed(() => list.value.reduce((max, b) => Math.max(max, b.sort_order), 0))

  function loadAll() { list.value = [...bannerApi.getAll()] }
  function add(data: Omit<Banner, 'id' | 'created_at' | 'updated_at'>) { bannerApi.create(data); loadAll() }
  function update(id: number, data: Partial<Banner>) { bannerApi.updateById(id, data); loadAll() }
  function remove(id: number) {
    bannerApi.deleteById(id)
    list.value = list.value.filter(b => Number(b.id) !== id)
  }

  loadAll()
  return { list, activeList, sortedList, maxSortOrder, add, update, remove }
})
