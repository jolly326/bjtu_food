import { ref } from 'vue'

/**
 * useAsyncGuard（UI-05）：列表页请求竞态守卫唯一实现。
 *
 * 背景：连续快速切换筛选 / 输入关键词会并发请求，过期响应晚到会覆盖最新数据（数据错乱）。
 * 此前操作日志 / 反馈 / 评价审核三个列表视图各自维护 `reqToken` 手工比对（三份重复样板），
 * 本收敛为唯一 composable（迁移顺序：OperationLogView → FeedbackView → ReviewManageView）。
 *
 * 契约（frontend-ui-consolidation.md UI-05）：
 *  - `run(fn)` 内部 `++token`、置 loading=true、清空 error、catch 填 error；
 *  - finally「仅未过期」置 loading=false；过期请求的 error / loading 不写；
 *  - **数据赋值留给调用方回调**（避免行为漂移）：fn 接收 `alive`（`() => boolean`，
 *    本次请求是否仍为最新），调用方在 await 落地后、写数据前自行校验 `alive()`；
 *    过期响应整份丢弃（不写 loading / error / 数据）。
 *
 * @example
 * const { loading, error, run } = useAsyncGuard()
 * const reload = () => run(async (alive) => {
 *   const res = await listFeedbacks(params)
 *   if (!alive()) return
 *   rows.value = res.list
 *   total.value = res.total
 * })
 */
export function useAsyncGuard() {
  const loading = ref(false)
  const error = ref('')
  let token = 0

  async function run(fn: (alive: () => boolean) => Promise<void>): Promise<void> {
    const current = ++token
    const alive = () => current === token
    loading.value = true
    error.value = ''
    try {
      await fn(alive)
    } catch (e: any) {
      if (alive()) error.value = e?.message || '加载失败'
    } finally {
      if (alive()) loading.value = false
    }
  }

  return { loading, error, run }
}
