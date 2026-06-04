import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Dish } from '@/types'
import { dishApi } from '@/api'

export const useDishStore = defineStore('dish', () => {
  const list = ref<Dish[]>([])
  const activeList = computed(() => list.value.filter(d => d.status === 'active'))

  function loadAll() { list.value = [...dishApi.getAll()] }
  function add(data: Omit<Dish, 'id' | 'created_at' | 'updated_at'>) { dishApi.create(data); loadAll() }
  function update(id: number, data: Partial<Dish>) { dishApi.updateById(id, data); loadAll() }
  function remove(id: number) { dishApi.deleteById(id); loadAll() }

  loadAll()
  return { list, activeList, add, update, remove }
})
