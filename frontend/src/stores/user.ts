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

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync(STORAGE_KEY_TOKEN) || '')
  const userInfo = ref<UserInfo | null>(loadUserInfo())
  const userStats = ref<UserStats>({ favoriteCount: 0, reviewCount: 0 })
  const loading = ref(false)

  /** 从缓存恢复登录态（App 启动时调用） */
  function restoreFromCache(): boolean {
    const saved = uni.getStorageSync(STORAGE_KEY_TOKEN)
    if (saved) {
      token.value = saved
      userInfo.value = loadUserInfo()
      return true
    }
    return false
  }

  async function login(code: string, studentId: string) {
    loading.value = true
    try {
      const res = await userApi.login(code, studentId)
      token.value = res.token
      userInfo.value = res.userInfo
      uni.setStorageSync(STORAGE_KEY_TOKEN, res.token)
      uni.setStorageSync(STORAGE_KEY_USER, JSON.stringify(res.userInfo))
    } catch (e: any) {
      throw new Error(e.message || '登录失败')
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
    uni.removeStorageSync(STORAGE_KEY_TOKEN)
    uni.removeStorageSync(STORAGE_KEY_USER)
  }

  function isLoggedIn(): boolean {
    return !!token.value && !!userInfo.value
  }

  async function fetchStats() {
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

  return { userInfo, userStats, token, loading, restoreFromCache, login, updateProfile, fetchStats, logout, isLoggedIn, requireAuth }
})
