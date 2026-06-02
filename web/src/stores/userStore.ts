import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { User } from '@/types'
import { userApi } from '@/api'

export const useUserStore = defineStore('user', () => {
  const list = ref<User[]>([])

  function loadAll() { list.value = userApi.getAll() }
  function toggleUserStatus(id: number) { userApi.toggleUserStatusById(id); loadAll() }
  function updateProfile(id: number, data: Partial<Pick<User, 'nickname' | 'password'>>) { userApi.updateUserProfileById(id, data); loadAll() }

  loadAll()
  return { list, toggleUserStatus, updateProfile }
})
