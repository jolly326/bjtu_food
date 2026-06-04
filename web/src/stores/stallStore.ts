import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Stall } from '@/types'
import { stallApi } from '@/api'

export const useStallStore = defineStore('stall', () => {
  const list = ref<Stall[]>([])
  const activeList = computed(() => list.value.filter(s => s.status === 'active'))

  function loadAll() { list.value = [...stallApi.getAll()] }
  function add(data: Omit<Stall, 'id' | 'created_at' | 'updated_at'>) { stallApi.create(data); loadAll() }
  function update(id: number, data: Partial<Stall>) { stallApi.updateById(id, data); loadAll() }
  function remove(id: number) { stallApi.deleteById(id); loadAll() }

  loadAll()
  return { list, activeList, add, update, remove }
})
