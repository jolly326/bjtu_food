/**
 * 运营端会话 store（登录态 + 账号名）。
 *
 * 令牌与账号名的持久化仍由 `api/request.ts` 负责（sessionStorage，同生命周期）；
 * 本 store 只承载会话状态与动作，供登录页、布局与守卫消费，避免各视图各自读写 storage。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { sessionApi } from '@/api'
import {
  clearToken,
  clearUsername,
  readToken,
  readUsername,
  writeToken,
  writeUsername,
} from '@/api/request'

export const useSessionStore = defineStore('session', () => {
  const token = ref(readToken())
  /** 无 token 不回显账号名（token 失效 / 未登录时顶栏不展示） */
  const username = ref(token.value ? readUsername() : '')

  const isAuthenticated = computed(() => !!token.value)

  async function login(user: string, password: string) {
    const res = await sessionApi.login(user, password)
    writeToken(res.token)
    writeUsername(res.username)
    token.value = res.token
    username.value = res.username
  }

  function logout() {
    clearToken()
    clearUsername()
    token.value = ''
    username.value = ''
  }

  return { token, username, isAuthenticated, login, logout }
})
