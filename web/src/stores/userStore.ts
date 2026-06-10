import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { User } from '@/types'
import { userApi } from '@/api'

export const useUserStore = defineStore('user', () => {
  const list = ref<User[]>([])

  async function loadAll() {
    list.value = await userApi.getAll()
  }

  async function add(data: Omit<User, 'id' | 'created_at' | 'updated_at'>) {
    await userApi.create(data)
    await loadAll()
  }

  async function remove(id: number) {
    await userApi.deleteById(id)
    await loadAll()
  }

  async function toggleUserStatus(id: number) {
    await userApi.toggleUserStatusById(id)
    await loadAll()
  }

  async function updateProfile(id: number, data: Partial<Pick<User, 'nickname' | 'password' | 'avatar' | 'username'>>) {
    await userApi.updateUserProfileById(id, data)
    await loadAll()
  }

  async function login(username: string, password: string): Promise<{ success: boolean; error?: string }> {
    try {
      const res = await userApi.login(username, password)
      localStorage.setItem('token', res.token)
      localStorage.setItem('username', res.username)
      return { success: true }
    } catch (e: any) {
      return { success: false, error: e.message || '管理员账号或密码错误' }
    }
  }

  loadAll()
  return { list, add, remove, toggleUserStatus, updateProfile, login }
})
