import { onShow } from '@dcloudio/uni-app'

/**
 * useOnShowRefresh —— 列表页 onShow 重拉闸门（MP-07）
 *
 * 列表页 onShow 不无条件全量重拉：从二级页返回且本次无改动时，重拉会多一次无意义请求、
 * 并重置列表与分页导致返回后位置丢失。故以闸门控制按需重拉。
 *
 * 重拉条件（命中任一，否则整段跳过、保留既有列表与分页）：
 * ① 首次进入（从未拉过）；
 * ② 本页发生过写操作（`markDirty()` 置脏后失效节流窗口）；
 * ③ 距上次重拉已超过 `throttleMs` —— 兜底保证长时间停留后台后数据不过期。
 *
 * 显式重拉（失败重试块 @tap）不经过本闸门：那是用户明确的意图，直接调用 load 即可。
 *
 * 状态为**页面实例级**（每次进页面重新创建），不做跨实例共享，避免 A 页刷新把 B 页闸门误置。
 *
 * ⚠️ 必须在组件实例上下文中调用（由页面 setup 同步调用）——内部注册了 `onShow`。
 */
export function useOnShowRefresh(
  load: () => void | Promise<void>,
  throttleMs: number = 30 * 1000,
) {
  /** 上次重拉时间（ms）；0 = 从未拉过 */
  let lastLoadAt = 0
  /** 本页是否发生过写操作（置脏后忽略节流窗口） */
  let dirty = false

  /** 置脏：本页发生写操作后调用，使下次 onShow 必然重拉 */
  function markDirty() {
    dirty = true
  }

  /** 不满足重拉条件则静默跳过（不重拉、不重置分页） */
  function refresh() {
    const now = Date.now()
    const neverLoaded = lastLoadAt === 0
    if (!neverLoaded && !dirty && now - lastLoadAt < throttleMs) return
    dirty = false
    lastLoadAt = now
    void load()
  }

  onShow(refresh)

  return { markDirty }
}
