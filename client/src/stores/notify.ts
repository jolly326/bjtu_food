import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUnreadCount } from '@/api/notify'

/** 系统通知未读计数（驱动首页/我的页红点，task-09） */
export const useNotifyStore = defineStore('notify', () => {
  const unreadCount = ref(0)

  async function fetchUnread() {
    try {
      unreadCount.value = await getUnreadCount()
    } catch {
      unreadCount.value = 0
    }
  }

  function reset() {
    unreadCount.value = 0
  }

  return { unreadCount, fetchUnread, reset }
})
