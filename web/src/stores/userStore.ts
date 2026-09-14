import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { User } from '@/types'
import { userApi } from '@/api'

export const useUserStore = defineStore('user', () => {
  const list = ref<User[]>([])

  /** 当前登录管理员 ID（回填自 profile.id），用于「禁止操作自己」等鉴权判断（C02） */
  const adminId = ref<number | null>(null)
  /** 当前登录管理员角色（缓存自 profile，减少路由守卫重复回源） */
  const role = ref<string>('')

  async function loadAll() {
    list.value = await userApi.getAll()
  }

  async function toggleUserStatus(id: number, targetStatus: 'active' | 'disabled') {
    await userApi.toggleUserStatusById(id, targetStatus)
    await loadAll()
  }

  /**
   * 回源 /auth/profile 并缓存到 store + localStorage。
   * 单点：登录成功后与路由守卫（缓存缺失/401 时）都走这里，避免重复请求（M11）。
   */
  async function loadProfile(): Promise<User> {
    const me = await userApi.getProfile()
    adminId.value = me && me.id != null ? Number(me.id) : null
    role.value = me?.role || ''
    if (adminId.value != null) localStorage.setItem('adminId', String(adminId.value))
    if (me?.username) localStorage.setItem('username', me.username)
    return me
  }

  /** 统一清理登录态（M09）：清除 localStorage 并重置 store 状态，避免残留上一账号信息 */
  function clearAuth() {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('adminId')
    localStorage.removeItem('rememberedUsername')
    adminId.value = null
    role.value = ''
  }

  // 注意：不再在 setup 顶层自动调用 loadAll()，避免未登录（无 token）时触发 getAll() 请求
  // 导致 http 拦截层 401 清 token 跳登录的副作用。list 由实际需要的页面显式调用 loadAll() 加载。
  return { list, adminId, role, loadAll, loadProfile, toggleUserStatus, clearAuth }
})
