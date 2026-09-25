/**
 * useOnShowRefresh —— 列表页 onShow 重拉闸门（MP-07）
 *
 * 列表页 onShow 不无条件全量重拉：从二级页返回且本次无改动时，重拉会多一次无意义请求、
 * 并重置列表与分页导致返回后位置丢失。故以闸门控制按需重拉。
 *
 * 规则（命中任一才重拉，否则整段跳过、保留既有列表与分页）：
 * ① 首次进入（从未拉过）；
 * ② 距上次重拉已超过 throttleMs（默认 30s）——兜底保证长时间停留后台后数据不过期。
 *
 * 显式重拉（失败重试块 @tap）不经过本闸门：那是用户明确的意图，直接调用 load 即可。
 * 若某页出现「本页写操作后必须重拉」的场景（如提交/删除后返回），调用 markDirty() 置脏，
 * 下次 onShow 必然重拉（置脏即失效节流窗口）。
 *
 * 状态为**页面实例级**（每次进页面重新创建），不做跨页面实例共享，避免 A 页刷新把 B 页闸门误置。
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

  /** 在 onShow 中调用；不满足条件则静默跳过（不重拉、不重置分页） */
  function refreshOnShow() {
    const now = Date.now()
    const neverLoaded = lastLoadAt === 0
    if (!neverLoaded && !dirty && now - lastLoadAt < throttleMs) return
    dirty = false
    lastLoadAt = now
    void load()
  }

  return { markDirty, refreshOnShow }
}
