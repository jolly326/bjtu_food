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

  function add(type: ToastMessage['type'], message: string, duration = 2500) {
    const id = ++_id
    messages.value.push({ id, type, message })
    setTimeout(() => {
      messages.value = messages.value.filter(m => m.id !== id)
    }, duration)
  }

  function success(msg: string) { add('success', msg) }
  function error(msg: string) { add('error', msg) }
  function info(msg: string) { add('info', msg) }

  return { messages, success, error, info }
})
