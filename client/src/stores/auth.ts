import { ref } from 'vue'
import { defineStore } from 'pinia'
import { PATH } from '@/utils/routes'

/**
 * 身份认证流程状态（独立页面形态）：
 * 需要认证的入口（requireAuth 守卫 / 4031 请求层）经 requestAuth 记录待办并跳转独立认证页，
 * 认证成功后由认证页返回原页，原页 onShow 经 consumePending 续接待办（§5.y）。
 * 本层不承载任何弹层显隐——认证表单承载于 pages/auth/index。
 */
export const useAuthStore = defineStore('auth', () => {
  /** 认证后待执行动作（认证成功返回原页后由 consumePending 续接） */
  const pendingAction = ref<(() => void) | null>(null)

  /** 发码冷却（秒）：页面化后跨进出页面持久——60s 内重进页面不重置，前端不辅助绕过冷却 */
  const codeCooldown = ref(0)
  let cooldownTimer: ReturnType<typeof setInterval> | null = null

  /** 需要认证的入口：记录待办（可选）并跳转独立认证页（未认证时调用） */
  function requestAuth(action?: () => void) {
    if (action) pendingAction.value = action
    uni.navigateTo({ url: PATH.auth })
  }

  /** 放弃认证（认证页未完成即离开）：清除待办，避免过期动作在后续认证成功后误执行 */
  function clearPending() {
    pendingAction.value = null
  }

  /** 认证成功返回原页后调用：执行并清空待办（由消费页 onShow 触发） */
  function consumePending() {
    const action = pendingAction.value
    pendingAction.value = null
    action?.()
  }

  /** 发码成功后启动 60s 冷却（单一持有、持续递减；已有剩余则沿用当前值不重置） */
  function startCooldown() {
    if (codeCooldown.value <= 0) codeCooldown.value = 60
    if (cooldownTimer) clearInterval(cooldownTimer)
    cooldownTimer = setInterval(() => {
      codeCooldown.value -= 1
      if (codeCooldown.value <= 0 && cooldownTimer) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }, 1000)
  }

  return { codeCooldown, requestAuth, clearPending, consumePending, startCooldown }
})
