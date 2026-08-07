import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 认证弹层（AuthSheet）显隐与「认证后待执行动作」状态。
 * 游客进入「我的」页可见完整界面（未认证态），
 * 点击需要认证的功能时 requireAuth() 记录待办并弹出认证表单，
 * 认证成功后弹层自动关闭并执行待办（如跳转到刚才点选的功能页）。
 */
export const useAuthSheetStore = defineStore('authSheet', () => {
  const visible = ref(false)
  const pendingAction = ref<(() => void) | null>(null)

  function show() {
    visible.value = true
  }

  function hide() {
    visible.value = false
  }

  /** 需要认证的入口：未登录则记录待办并弹认证；已登录直接执行 */
  function requireAuth(action: () => void) {
    if (visible.value) return
    pendingAction.value = action
    show()
  }

  /** 认证成功后调用：关闭弹层并执行待办（由 AuthSheet 监听登录态变化触发） */
  function runPending() {
    hide()
    const action = pendingAction.value
    pendingAction.value = null
    action?.()
  }

  return { visible, pendingAction, show, hide, requireAuth, runPending }
})
