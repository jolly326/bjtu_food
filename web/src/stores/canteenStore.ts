import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Canteen } from '@/types'
import { canteenApi } from '@/api'

export const useCanteenStore = defineStore('canteen', () => {
  const list = ref<Canteen[]>([])
  const activeList = ref<Canteen[]>([])

  async function loadAll() {
    const data = await canteenApi.getAll()
    list.value = data
    activeList.value = data.filter(c => c.status === 'active')
  }
  async function add(data: Omit<Canteen, 'id' | 'created_at' | 'updated_at'>) { await canteenApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Canteen>) { await canteenApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await canteenApi.deleteById(id); await loadAll() }

  loadAll()
  return { list, loadAll, activeList, add, update, remove }
})
