import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { FoodList, ListCreate } from '@/types/list'
import * as listApi from '@/api/list'

export const useListStore = defineStore('list', () => {
  const foodList = ref<FoodList[]>([])

  async function fetchMyLists() {
    foodList.value = await listApi.getMyLists()
  }

  async function createList(data: ListCreate) {
    const newList = await listApi.createList(data)
    foodList.value.push(newList)
    return newList
  }

  return { foodList, fetchMyLists, createList }
})
