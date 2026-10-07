/**
 * 请求序号守卫（**两端同构**，对齐 client `composables/usePagedList.ts` 的 `createSeqGuard`）。
 *
 * <p>**存在理由**：并发请求返回顺序可能与发起顺序不同。若「后一次请求」先返回、后一次请求后返回，
 * 后到的旧响应会**覆盖**新结果 ⇒ 界面条件与表格内容不一致。
 * 常见触发：用户快速连续切筛选 / 切页、拖拽排序后立刻刷新、删除后刷新撞上在途加载。
 *
 * <p>**口径**：每次发起请求先 `begin()` 取号；响应到达后 `isCurrent(seq)` 为`false`
 * 即表示**已被更新的请求淘汰**，直接丢弃本次结果（不写状态、不弹错误）。
 *
 * <p>与「loading 时直接 return」的区别：后者是**丢弃后一次请求**（用户最后一次筛选变更被静默忽略、
 * 表格停在旧结果）；本守卫是**丢弃过期响应**（每次请求都发出，返回时按新旧裁决）——
 * 后者才是正确语义。
 */
export interface SeqGuard {
  /** 发起请求前取号（自增） */
  begin: () => number
  /** 响应到达后判定：`false` = 已被更新的请求淘汰，应丢弃本次结果 */
  isCurrent: (seq: number) => boolean
  /** 读取当前号但**不取号**：供「借当前号判定自己是否被淘汰」的旁路场景用 */
  peek: () => number
  /** 作废所有在途请求（只自增不取号） */
  invalidate: () => void
}

export function createSeqGuard(): SeqGuard {
  let current = 0
  return {
    begin: () => ++current,
    isCurrent: (seq) => seq === current,
    peek: () => current,
    invalidate: () => {
      current += 1
    },
  }
}
