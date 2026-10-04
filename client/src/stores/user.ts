import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/types/user'
import * as userApi from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER } from '@/constants/storage'

/** 从 storage 读回用户资料；损坏 / 缺失一律降级为 null（不抛，游客态可继续浏览） */
function loadUserInfo(): UserInfo | null {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY_USER)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 静默登录进行中标志（供启动流程并发去重） */
const silentLoginPending = ref(false)
let silentLoginPromise: Promise<void> | null = null

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync(STORAGE_KEY_TOKEN) || '')
  const userInfo = ref<UserInfo | null>(loadUserInfo())
  const loading = ref(false)

  /**
   * 用户资料持久化**单一出口**：内存态与 storage 必须同步更新，否则换页 / 重启后漂移。
   */
  function persistUserInfo(info: UserInfo | null) {
    userInfo.value = info
    if (info) uni.setStorageSync(STORAGE_KEY_USER, JSON.stringify(info))
    else uni.removeStorageSync(STORAGE_KEY_USER)
  }

  /** 登录态落盘：token 与资料成对写入（内存态与 storage 同步，避免换页 / 重启后漂移） */
  function saveAuth(tokenValue: string, info: UserInfo) {
    token.value = tokenValue
    uni.setStorageSync(STORAGE_KEY_TOKEN, tokenValue)
    persistUserInfo(info)
  }

  /**
   * 最近一次静默登录的失败原因：silentLogin 刻意不抛错（保证游客可继续浏览），
   * 但原因必须可查证 —— 否则「未配置 / 凭证无效 / 服务不可用」等部署事故在端上只剩间接症状。
   */
  const lastLoginError = ref('')

  /**
   * 微信静默登录：微信打开小程序自动登录为游客态（bindEmail 为空）。
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
        // 已有登录态：直接复用，仅尝试刷新资料（游客/认证态均读 GET /auth/profile）
        if (token.value) {
          try {
            persistUserInfo(await userApi.getProfile())
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
        saveAuth(res.token, res.userInfo)
        lastLoginError.value = ''
        // #endif
        // #ifndef MP-WEIXIN
        // 非微信端（H5 联调）：无 code 静默登录，保留本地游客态（无 token 亦可浏览）
        // #endif
      } catch (e) {
        // 静默登录失败：保留本地登录态（若已存在），不阻断浏览；记录原因供上层提示/排障（AUD-BE-02）
        lastLoginError.value = (e as Error)?.message || '微信登录失败'
        console.error('静默登录失败', e)
      } finally {
        silentLoginPending.value = false
        silentLoginPromise = null
      }
    })()
    return silentLoginPromise
  }

  /** 学号邮箱认证：验证码绑定当前微信 → 写入 bindEmail（认证判据即其非空），返回最新 userInfo */
  async function verifyEmail(code: string) {
    // 兜底：认证需微信登录态，若静默登录未就绪（如启动竞态）或失败，先补一次。
    // 透传真实失败原因（如「微信登录未配置」/「凭证无效」），避免误导为网络问题。
    if (!isLoggedIn()) {
      // silentLogin 本身不 reject（上文 catch），故此处 try/catch 无实际捕获；改为读取 lastLoginError 透出真实原因
      lastLoginError.value = ''
      await silentLogin()
      if (!isLoggedIn()) {
        throw new Error(lastLoginError.value || '微信登录未完成，请稍后重试')
      }
    }
    loading.value = true
    try {
      // 仅刷新 userInfo：JWT 不含 bind_email、后端实时查库判定认证态，无需（也不应）替换 token
      const info = await userApi.verifyEmail(code)
      persistUserInfo(info)
      return info
    } finally {
      loading.value = false
    }
  }

  async function updateProfile(data: { nickname?: string; avatar?: string }) {
    const res = await userApi.updateProfile(data)
    persistUserInfo(res)
    return res
  }

  /** 统一清登录态：清内存态 + 清 storage；被请求层 401/403 复用，避免登录态分裂。
   *  同时联动重置各业务 store 的「用户态数据」（通知红点等），避免换用户后串数据。
   *  用动态 import 避免 store 间的循环依赖。 */
  function forceLogout() {
    token.value = ''
    uni.removeStorageSync(STORAGE_KEY_TOKEN)
    persistUserInfo(null)
    void import('@/stores/notify')
      .then(({ useNotifyStore }) => useNotifyStore().reset())
      .catch(() => { /* store 未初始化 / 分包未加载，忽略 */ })
  }

  /** 是否有登录态（token+userInfo；微信静默登录后恒为 true，即游客态） */
  function isLoggedIn(): boolean {
    return !!token.value && !!userInfo.value
  }

  /**
   * 是否已邮箱认证（权限矩阵）：true 解锁 UGC 写操作；false = 游客态。
   *
   * **唯一判据 = `bindEmail` 非空**：出参不含 `verified` 布尔（与 bindEmail 同源冗余），
   * 故全端判定收敛在本方法一处；页面 / 组件不得散写 `!!userInfo.bindEmail` 造成判据分裂。
   */
  function isVerified(): boolean {
    return !!userInfo.value?.bindEmail
  }

  /**
   * 需认证入口守卫：未认证时跳转认证页并返回 false（认证成功返回后由原页 onShow 续接该动作）；
   * 已认证返回 true 直接执行。
   */
  function requireAuth(action?: () => void): boolean {
    if (!isVerified()) {
      useAuthStore().requestAuth(action)
      return false
    }
    return true
  }

  // token / lastLoginError 零外部消费，收敛为内部状态：前者供请求层 getStorageSync，
  // 后者供本 store 内静默登录失败透传。
  return {
    userInfo,
    silentLogin,
    verifyEmail,
    updateProfile,
    forceLogout,
    isLoggedIn,
    isVerified,
    requireAuth,
  }
})