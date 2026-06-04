import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Canteen } from '@/types'
import { canteenApi } from '@/api'

export const useCanteenStore = defineStore('canteen', () => {
  const list = ref<Canteen[]>([])
  const activeList = computed(() => list.value.filter(c => c.status === 'active'))

  function loadAll() { list.value = [...canteenApi.getAll()] }
  function add(data: Omit<Canteen, 'id' | 'created_at' | 'updated_at'>) { canteenApi.create(data); loadAll() }
  function update(id: number, data: Partial<Canteen>) { canteenApi.updateById(id, data); loadAll() }
  function remove(id: number) { canteenApi.deleteById(id); loadAll() }

  loadAll()
  return { list, activeList, add, update, remove }
})
