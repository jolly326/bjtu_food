import { ref } from 'vue'
import { defineStore } from 'pinia'

/** 仅本 store 内部使用（消息结构 + 类型收敛），故不对外导出 */
interface ToastMessage {
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

  // 仅保留 success / error：info 与 clear 在全仓无消费方（阶段1 死代码清理），已删除。
  function success(msg: string) { add('success', msg) }
  function error(msg: string) { add('error', msg, 4000) }  // 错误信息停留更久，方便阅读

  return { messages, success, error }
})
