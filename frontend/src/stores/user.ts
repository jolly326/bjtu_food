import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/types/user'
import * as userApi from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(null)
  const token = ref(uni.getStorageSync('token') || 'mock_token_2024')
  const loading = ref(false)

  async function login(code: string) {
    loading.value = true
    try {
      const res = await userApi.login(code)
      token.value = res.token
      userInfo.value = res.userInfo
      uni.setStorageSync('token', res.token)
    } catch (e: any) {
      throw new Error(e.message || '登录失败')
    } finally {
      loading.value = false
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    uni.removeStorageSync('token')
  }

  function isLoggedIn(): boolean {
    return !!token.value
  }

  function requireAuth(): boolean {
    if (!isLoggedIn()) {
      uni.showToast({ title: '请先登录', icon: 'none' })
      return false
    }
    return true
  }

  return { userInfo, token, loading, login, logout, isLoggedIn, requireAuth }
})
