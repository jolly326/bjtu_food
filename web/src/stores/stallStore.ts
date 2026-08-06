import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Stall } from '@/types'
import { stallApi } from '@/api'

export const useStallStore = defineStore('stall', () => {
  const list = ref<Stall[]>([])
  const activeList = ref<Stall[]>([])

  async function loadAll() {
    const data = await stallApi.getAll()
    list.value = data
    activeList.value = data.filter(s => s.status === 'active')
  }
  async function add(data: Omit<Stall, 'id' | 'created_at' | 'updated_at'>) { await stallApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Stall>) { await stallApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await stallApi.deleteById(id); await loadAll() }

  loadAll()
  return { list, loadAll, activeList, add, update, remove }
})
