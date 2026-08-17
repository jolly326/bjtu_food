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

  async function toggleUserStatus(id: number) {
    await userApi.toggleUserStatusById(id)
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

  async function login(username: string, password: string): Promise<{ success: boolean; error?: string }> {
    try {
      const res = await userApi.login(username, password)
      // 已知折中（M12）：token 维持 localStorage，非 httpOnly Cookie，存在 XSS 窃取风险但免跨端改动。
      localStorage.setItem('token', res.token)
      localStorage.setItem('username', res.username)
      // 登录接口本身不返回 adminId/role，登录成功后立即回源 profile 回填（C02 + M11 缓存）
      try {
        await loadProfile()
      } catch { /* profile 失败不阻断登录，守卫会兜底 */ }
      return { success: true }
    } catch (e: any) {
      return { success: false, error: e.message || '管理员账号或密码错误' }
    }
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

  loadAll()
  return { list, adminId, role, loadAll, loadProfile, toggleUserStatus, login, clearAuth }
})
