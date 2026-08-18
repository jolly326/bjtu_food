import { ref } from 'vue'
import { defineStore } from 'pinia'

export interface ToastMessage {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

export const useToastStore = defineStore('toast', () => {
  const messages = ref<ToastMessage[]>([])
  let _id = 0
  // 记录每个 toast 的定时器句柄，便于主动清理（L02）
  const timers = new Map<number, ReturnType<typeof setTimeout>>()

  function add(type: ToastMessage['type'], message: string, duration = 2500) {
    const id = ++_id
    messages.value.push({ id, type, message })
    const timer = setTimeout(() => {
      remove(id)
    }, duration)
    timers.set(id, timer)
  }

  function remove(id: number) {
    const timer = timers.get(id)
    if (timer) { clearTimeout(timer); timers.delete(id) }
    messages.value = messages.value.filter(m => m.id !== id)
  }

  function success(msg: string) { add('success', msg) }
  function error(msg: string) { add('error', msg, 4000) }  // 错误信息停留更久，方便阅读
  function info(msg: string) { add('info', msg) }

  /**
   * 清空全部 toast（如登出/切换账号时调用，避免残留上一账号的提示）。
   * 全局单例无组件卸载销毁问题，常规自动消失由 setTimeout 负责，无需额外 onUnmounted 清理。
   */
  function clear() {
    timers.forEach((t) => clearTimeout(t))
    timers.clear()
    messages.value = []
  }

  return { messages, success, error, info, clear }
})
