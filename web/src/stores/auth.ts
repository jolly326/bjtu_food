import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import { setBearerToken, clearBearerToken } from '@/api/http'
import type { AdminVO } from '@/types/common'

const TOKEN_KEY = 'admin_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const admin = ref<AdminVO | null>(null)

  // 初始化时把持久化令牌注入 http 层
  if (token.value) setBearerToken(token.value)

  function applyToken(t: string | null): void {
    token.value = t
    if (t) {
      localStorage.setItem(TOKEN_KEY, t)
      setBearerToken(t)
    } else {
      localStorage.removeItem(TOKEN_KEY)
      clearBearerToken()
    }
  }

  async function login(username: string, password: string): Promise<void> {
    const res = await authApi.login(username, password)
    applyToken(res.token)
    admin.value = res.admin
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) await authApi.logout()
    } catch {
      // 忽略登出接口异常，仍清本地态
    }
    applyToken(null)
    admin.value = null
  }

  async function fetchMe(): Promise<AdminVO> {
    admin.value = await authApi.me()
    return admin.value
  }

  const isLoggedIn = (): boolean => !!token.value

  return { token, admin, login, logout, fetchMe, isLoggedIn, applyToken }
})
