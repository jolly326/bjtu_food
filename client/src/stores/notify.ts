import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUnreadCount } from '@/api/notify'

/** 系统通知未读计数（驱动首页/我的页红点） */
export const useNotifyStore = defineStore('notify', () => {
  const unreadCount = ref(0)

  /**
   * 未读数请求去重（in-flight 合并 + 10s TTL 缓存 + 变更后强制刷新）。
   *
   * · **in-flight 合并**：已有请求在途时复用同一 Promise，避免并发重复打网络；
   * · **10s TTL**：TTL 内重复调用直接复用上次结果（页面来回切不重复请求同一数据）；
   * · **force**：本地发生读操作（单条已读 / 全部已读）后强制刷新，避免红点滞后；
   * · 失败**不**写入 TTL（下次调用立即重试），红点归零行为与既有口径一致。
   */
  const UNREAD_TTL_MS = 10_000
  let lastFetchedAt = 0
  let inflight: Promise<void> | null = null

  async function fetchUnread(opts: { force?: boolean } = {}) {
    if (inflight) return inflight
    if (!opts.force && Date.now() - lastFetchedAt < UNREAD_TTL_MS) return
    inflight = (async () => {
      try {
        unreadCount.value = await getUnreadCount()
        lastFetchedAt = Date.now()
      } catch {
        unreadCount.value = 0
      } finally {
        inflight = null
      }
    })()
    return inflight
  }

  /** 登录态变更（登出/换用户）时清零未读红点，避免残留上一用户计数 */
  function reset() {
    unreadCount.value = 0
    lastFetchedAt = 0
  }

  return { unreadCount, fetchUnread, reset }
})
