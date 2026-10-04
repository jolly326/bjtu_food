/**
 * 登录态 / 授权失败的统一处置（401、4031、403-需微信登录）。
 *
 * 与传输、请求编排解耦：本模块只回答「会话出问题后做什么」，不碰请求本身。
 * store 一律动态 import，避免 store ↔ 请求层的循环依赖；不再使用全局事件总线
 * （uni.$on/$emit 在 HMR / 模块重复加载下会重复订阅泄漏）。
 */
import { toastInfo } from '@/utils/error'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER } from '@/constants/storage'

/** 401 处理进行中标志：并发 401（首页多请求同时失效）不重复触发登出 + 重登 + Toast 风暴 */
let handling = false
/** 「登录已失效」Toast 冷却窗口：同文案 5s 内不重复（部署事故期连续 401 时防 Toast 风暴） */
const TOAST_COOLDOWN_MS = 5000
let lastToastAt = 0

/** 401：清本地登录态 + Toast + 重新微信静默登录（游客态自动恢复）；并发去重，结束后延迟复位 */
export async function handleUnauthorized(): Promise<void> {
  if (handling) return
  handling = true
  try {
    const { useUserStore } = await import('@/stores/user')
    const userStore = useUserStore()
    userStore.forceLogout()
    const now = Date.now()
    if (now - lastToastAt > TOAST_COOLDOWN_MS) {
      lastToastAt = now
      toastInfo('登录已失效，正在重新登录')
    }
    await userStore.silentLogin()
  } catch {
    // 极端情况（动态 import 失败）：直接清 storage 兜底
    uni.removeStorageSync(STORAGE_KEY_TOKEN)
    uni.removeStorageSync(STORAGE_KEY_USER)
  } finally {
    // 延迟复位，确保后续真正失效的 401 能再次触发引导
    setTimeout(() => { handling = false }, 300)
  }
}

/** 4031 邮箱未认证：提示并跳独立认证页；与普通 403 严格分流（403 不跳，避免误导用户去改邮箱） */
export async function handleUnverified(): Promise<void> {
  toastInfo('请先完成身份认证')
  try {
    const { useAuthStore } = await import('@/stores/auth')
    useAuthStore().requestAuth()
  } catch {
    // 认证页跳转失败时仅保留提示
  }
}

/** 403 是否属于「需微信登录」（已认证但账号缺 openid，message 含「微信登录」） */
export function isWechatLoginRequired(msg: string): boolean {
  return /微信登录/.test(msg)
}

/**
 * 403-需微信登录：弹窗说明 + 用户点「重新登录」确认后，才重跑微信静默登录补 openid。
 *
 * **禁止自动重登**：对「无 openid 的历史学号账号」自动重登会静默切到新游客号
 * （登录态无感知互换，且新号认证态为空，重试仍撞 4031）。取消 / 弹窗失败一律只保留提示。
 * 不弹邮箱认证引导 —— 邮箱已认证，弹它属误导。
 */
export function handleWechatLoginRequired(msg: string): void {
  uni.showModal({
    title: '需要微信登录',
    content: msg,
    confirmText: '重新登录',
    showCancel: true,
    success: (r) => {
      if (!r.confirm) return
      void (async () => {
        try {
          const { useUserStore } = await import('@/stores/user')
          const userStore = useUserStore()
          // 必须经 wx.login 重新取 code 换号（后端按 openid 绑定）；silentLogin 在「已有 token」
          // 分支只刷新 profile，不会补 openid —— 故先清本地态再跑完整静默登录
          userStore.forceLogout()
          await userStore.silentLogin(true)
          toastInfo('已重新登录，请重试')
        } catch {
          toastInfo('重新登录未完成，请稍后重试')
        }
      })()
    },
    fail: () => toastInfo(msg),
  })
}

/** 401 重试前置：确保静默登录完成（拿到 token）；成功返回 true 表示可原样重试一次 */
export async function trySilentRelogin(): Promise<boolean> {
  try {
    const { useUserStore } = await import('@/stores/user')
    await useUserStore().silentLogin()
    return true
  } catch {
    return false
  }
}
