import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/types/user'
import * as userApi from '@/api/user'
import { useAuthSheetStore } from '@/stores/authSheet'

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

/** 静默登录进行中标志（供启动流程并发去重） */
const silentLoginPending = ref(false)
let silentLoginPromise: Promise<void> | null = null

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync(STORAGE_KEY_TOKEN) || '')
  const userInfo = ref<UserInfo | null>(loadUserInfo())
  const loading = ref(false)

  /**
   * 微信静默登录（§5.y）：微信打开小程序自动登录为游客态（verified=false）。
   * - 已有 token → 刷新 /auth/profile 资料（游客态即可读），失败则重登；
   * - 无 token → wx.login 拿 code → POST /auth/wechat-login 静默建号/取号。
   * 并发去重：同一时间仅执行一次（App 启动 / 401 重登 / 页面 onLoad 并发安全）。
   */
  async function silentLogin(force = false): Promise<void> {
    if (silentLoginPromise && !force) return silentLoginPromise
    if (silentLoginPending.value && !force) return
    silentLoginPending.value = true
    silentLoginPromise = (async () => {
      try {
        // 已有登录态：直接复用，仅尝试刷新资料（游客/认证态均读 GET /auth/profile，见 §5.y.5）
        if (token.value) {
          try {
            const fresh = await userApi.getProfile()
            userInfo.value = fresh
            uni.setStorageSync(STORAGE_KEY_USER, JSON.stringify(fresh))
            return
          } catch {
            // 资料刷新失败（token 失效）：清空后走静默登录重登
            forceLogout()
          }
        }
        // 无 token：wx.login 静默登录（小程序端）
        // #ifdef MP-WEIXIN
        const code = await new Promise<string>((resolve, reject) => {
          uni.login({
            provider: 'weixin',
            success: (r) => resolve(r.code),
            fail: () => reject(new Error('微信登录失败')),
          })
        })
        const res = await userApi.wechatLogin(code)
        token.value = res.token
        userInfo.value = res.userInfo
        saveAuth(res.token, res.userInfo)
        // #endif
        // #ifndef MP-WEIXIN
        // 非微信端（H5 联调）：无 code 静默登录，保留本地游客态（无 token 亦可浏览）
        // #endif
      } catch (e) {
        // 静默登录失败：保留本地登录态（若此前存在），不阻断浏览
        console.error('静默登录失败', e)
      } finally {
        silentLoginPending.value = false
        silentLoginPromise = null
      }
    })()
    return silentLoginPromise
  }

  /** 学号邮箱认证（§5.y）：验证码绑定当前微信 → verified=true，返回最新 userInfo */
  async function verifyEmail(code: string) {
    // 兜底：认证需微信登录态，若静默登录未就绪（如启动竞态）或失败，先补一次。
    // 透传真实失败原因（如「微信登录未配置」/「凭证无效」），避免误导为网络问题。
    if (!isLoggedIn()) {
      try {
        await silentLogin()
      } catch (e) {
        throw new Error((e as Error)?.message || '微信登录未完成，请稍后重试')
      }
      if (!isLoggedIn()) {
        throw new Error('微信登录未完成，请稍后重试')
      }
    }
    loading.value = true
    try {
      const res = await userApi.verifyEmail(code)
      token.value = res.token
      userInfo.value = res.userInfo
      saveAuth(res.token, res.userInfo)
      return res.userInfo
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

  /** 统一清登录态：清内存态 + 清 storage；被 http 层 401/403 事件复用，避免登录态分裂。
   * 同时联动重置各业务 store 的「用户态数据」（评价列表有用标记、猜你喜欢、通知红点等），
   * 避免换用户后残留上一用户的个性化数据导致串数据（§5.x 登录态一致性）。
   * 用动态 import 避免 store 间的循环依赖。 */
  function forceLogout() {
    token.value = ''
    userInfo.value = null
    uni.removeStorageSync(STORAGE_KEY_TOKEN)
    uni.removeStorageSync(STORAGE_KEY_USER)
    // 联动重置：评价列表有用标记 / 猜你喜欢个性化推荐
    void import('@/stores/dish').then(({ useDishStore }) => {
      try { useDishStore().resetUserScopedData() } catch { /* 忽略未初始化 */ }
    }).catch(() => {})
    // 联动重置：通知未读数红点
    void import('@/stores/notify').then(({ useNotifyStore }) => {
      try { useNotifyStore().reset() } catch { /* 忽略未初始化 */ }
    }).catch(() => {})
  }

  /** 是否有登录态（token+userInfo；微信静默登录后恒为 true，即游客态） */
  function isLoggedIn(): boolean {
    return !!token.value && !!userInfo.value
  }

  /** 是否已邮箱认证（§5.y）：true 解锁社区写操作；false = 游客态 */
  function isVerified(): boolean {
    return userInfo.value?.verified === true
  }

  /**
   * 需认证入口守卫（§5.y 权限矩阵）：未认证（verified=false）时弹出认证引导（AuthSheet）并返回 false，
   * 已认证（verified=true）返回 true 直接执行 action。
   * 传入 action 时：认证成功后由 AuthSheet 自动执行该动作（游客操作 → 认证 → 自动继续原动作）。
   */
  function requireAuth(action?: () => void): boolean {
    if (!isVerified()) {
      if (action) {
        useAuthSheetStore().requireAuth(action)
      } else {
        useAuthSheetStore().show()
      }
      return false
    }
    return true
  }

  return {
    userInfo,
    token,
    loading,
    silentLogin,
    verifyEmail,
    updateProfile,
    forceLogout,
    isLoggedIn,
    isVerified,
    requireAuth,
  }
})
