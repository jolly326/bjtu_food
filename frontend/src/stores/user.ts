import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo, UserStats } from '@/types/user'
import * as userApi from '@/api/user'

const STORAGE_KEY_TOKEN = 'token'
const STORAGE_KEY_USER = 'userInfo'

function loadUserInfo(): UserInfo | null {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY_USER)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function saveAuth(tokenValue: string, info: UserInfo) {
  uni.setStorageSync(STORAGE_KEY_TOKEN, tokenValue)
  uni.setStorageSync(STORAGE_KEY_USER, JSON.stringify(info))
}

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync(STORAGE_KEY_TOKEN) || '')
  const userInfo = ref<UserInfo | null>(loadUserInfo())
  const userStats = ref<UserStats>({ likeCount: 0, reviewCount: 0 })
  const loading = ref(false)

  function restoreFromCache(): boolean {
    const saved = uni.getStorageSync(STORAGE_KEY_TOKEN)
    if (saved) {
      token.value = saved
      userInfo.value = loadUserInfo()
      return true
    }
    return false
  }

  async function loginByPassword(account: string, password: string) {
    loading.value = true
    try {
      const res = await userApi.loginByPassword(account, password)
      token.value = res.token
      userInfo.value = res.userInfo
      saveAuth(res.token, res.userInfo)
      await fetchStats()
    } finally {
      loading.value = false
    }
  }

  async function loginByEmailCode(email: string, code: string) {
    loading.value = true
    try {
      const res = await userApi.loginByEmailCode(email, code)
      token.value = res.token
      userInfo.value = res.userInfo
      saveAuth(res.token, res.userInfo)
      await fetchStats()
    } finally {
      loading.value = false
    }
  }

  async function register(data: { username: string; email: string; code: string; password: string; nickname: string }) {
    loading.value = true
    try {
      return await userApi.register(data)
    } finally {
      loading.value = false
    }
  }

  async function updateProfile(data: { nickname?: string; avatar?: string }) {
    const res = await userApi.updateProfile(data)
    userInfo.value = res
    uni.setStorageSync(STORAGE_KEY_USER, JSON.stringify(res))
    return res
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    userStats.value = { likeCount: 0, reviewCount: 0 }
    uni.removeStorageSync(STORAGE_KEY_TOKEN)
    uni.removeStorageSync(STORAGE_KEY_USER)
  }

  function isLoggedIn(): boolean {
    return !!token.value && !!userInfo.value
  }

  async function fetchStats() {
    if (!isLoggedIn()) return
    try {
      userStats.value = await userApi.getUserStats()
    } catch {
      console.error('获取用户统计失败')
    }
  }

  function requireAuth(): boolean {
    if (!isLoggedIn()) {
      uni.showToast({ title: '请先登录', icon: 'none' })
      return false
    }
    return true
  }

  return {
    userInfo,
    userStats,
    token,
    loading,
    restoreFromCache,
    loginByPassword,
    loginByEmailCode,
    register,
    updateProfile,
    fetchStats,
    logout,
    isLoggedIn,
    requireAuth,
  }
})
