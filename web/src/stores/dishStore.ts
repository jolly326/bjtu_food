import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Dish } from '@/types'
import { dishApi } from '@/api'

export const useDishStore = defineStore('dish', () => {
  const list = ref<Dish[]>([])
  const activeList = ref<Dish[]>([])

  async function loadAll() {
    const data = await dishApi.getAll()
    list.value = data
    activeList.value = data.filter(d => d.status === 'active')
  }
  async function add(data: Omit<Dish, 'id' | 'created_at' | 'updated_at'>) { await dishApi.create(data); await loadAll() }
  async function update(id: number, data: Partial<Dish>) { await dishApi.updateById(id, data); await loadAll() }
  async function remove(id: number) { await dishApi.deleteById(id); await loadAll() }

  loadAll()
  return { list, loadAll, activeList, add, update, remove }
})
