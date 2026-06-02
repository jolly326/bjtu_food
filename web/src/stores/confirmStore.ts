import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useConfirmStore = defineStore('confirm', () => {
  const visible = ref(false)
  const message = ref('')
  let _resolve: ((v: boolean) => void) | null = null

  function confirm(msg: string): Promise<boolean> {
    message.value = msg
    visible.value = true
    return new Promise(r => { _resolve = r })
  }

  function ok() {
    visible.value = false
    _resolve?.(true)
    _resolve = null
  }

  function cancel() {
    visible.value = false
    _resolve?.(false)
    _resolve = null
  }

  return { visible, message, confirm, ok, cancel }
})
