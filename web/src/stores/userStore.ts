import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { User } from '@/types'
import { userApi } from '@/api'

export const useUserStore = defineStore('user', () => {
  const list = ref<User[]>([])

  async function loadAll() { list.value = await userApi.getAll() }
  async function add(data: Omit<User, 'id' | 'created_at' | 'updated_at'>) { await userApi.create(data); await loadAll() }
  async function remove(id: number) { await userApi.deleteById(id); await loadAll() }
  async function toggleUserStatus(id: number) { await userApi.toggleUserStatusById(id); await loadAll() }
  async function updateProfile(id: number, data: Partial<Pick<User, 'nickname' | 'password' | 'avatar' | 'username'>>) { await userApi.updateUserProfileById(id, data); await loadAll() }

  async function login(username: string, password: string): Promise<{ success: boolean; error?: string }> {
    try {
      const res = await userApi.login(username, password)
      localStorage.setItem('token', res.token)
      localStorage.setItem('username', res.username)
      return { success: true }
    } catch {
      console.log('[user] login 降级到本地校验')
      const admin = list.value.find(u => u.role === 'admin')
      if (!admin) return { success: false, error: '系统未配置管理员' }
      if (username !== admin.username || password !== admin.password) return { success: false, error: '管理员账号或密码错误' }
      localStorage.setItem('token', 'logged_in')
      localStorage.setItem('username', username)
      return { success: true }
    }
  }

  loadAll()
  return { list, add, remove, toggleUserStatus, updateProfile, login }
})
