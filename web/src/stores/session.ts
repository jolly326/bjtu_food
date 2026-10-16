/**
 * 运营端会话 store（登录态 + 账号名）。
 *
 * 令牌的持久化仍由 `api/request.ts` 负责（sessionStorage）；本 store 只承载会话状态与动作，
 * 供登录页、布局与守卫消费，避免各视图各自读写 storage。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { sessionApi } from '@/api'
import { clearToken, readToken, writeToken } from '@/api/request'

export const useSessionStore = defineStore('session', () => {
  const token = ref(readToken())
  const username = ref('')

  const isAuthenticated = computed(() => !!token.value)

  async function login(user: string, password: string) {
    const res = await sessionApi.login(user, password)
    writeToken(res.token)
    token.value = res.token
    username.value = res.username
  }

  function logout() {
    clearToken()
    token.value = ''
    username.value = ''
  }

  return { token, username, isAuthenticated, login, logout }
})
