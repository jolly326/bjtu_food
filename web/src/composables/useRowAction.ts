import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fail } from '@/utils/error'

/**
 * 行内 / 抽屉动作的**并发保护骨架**（全站唯一实现，[列表页模板 §1.3](../../../docs/ui/web/列表页模板.md)）。
 *
 * <p><b>存在理由</b>：列表页的「启用 / 停用 / 删除 / 处置」是一组高相似度动作，若各视图各写一遍
 * `busyId=id → try{action; 提示; 刷新; 关抽屉} catch{fail} finally{清锁}`，改并发语义（加二次确认、
 * 手抄导致改并发语义（加二次确认、加节流、调整刷新顺序）必漏，且各视图的**回填守卫**详略不一
 * （有的是 `if (current?.id === id)`，有的完全没守卫 ⇒ 抽屉显示 A 行时点B 行的按钮会串数据）。
 *
 * <p><b>统一后的执行顺序**（幂等，顺序固定）：
 * `busyId=id → action() → 成功提示 → refresh() → closeDrawer() → syncAfterRefresh(id) → finally 清 busyId`。
 * 其中 `refresh` / `closeDrawer` / `syncAfterRefresh` 三步均可省略 ⇒ 各视图只声明自己真正需要的。
 *
 * <p><b>用法</b>：视图内**只调一次** `useRowAction()`，本文件多个动作共用同一把锁
 * （否则同一行会被两把锁分别置灰，出现「锁不住」的漏洞）：
 *
 * ```ts
 * const { isBusy, runRowAction } = useRowAction()
 *
 * function toggle(row: BannerAdminVO) {
 *   const next = row.status === 'on' ? 'off' : 'on'
 *   return runRowAction({
 *     id: row.id,
 *     action: () => updateBannerStatus(row.id, next),
 *     successMessage: next === 'on' ? '已启用' : '已停用',
 *     refresh: load,
 *     syncAfterRefresh: syncCurrent,
 *   })
 * }
 * ```
 *
 * 模板置灰统一用 `isBusy(row.id)` / `isBusy(current?.id)`（**禁止**直接读 `busyId === row.id`）。
 */

/** 单个行内动作的参数（可选步骤按需声明，不需要的直接省略） */
export interface RowActionOptions {
  /** 目标行 id（并发锁与回填定位的唯一依据） */
  id: number
  /** 动作本体；把「二次确认」等前置步骤一并放进这个闭包（确认取消时 `throw` / 直接 `return` 即可） */
  action: () => Promise<unknown>
  /** 成功提示文案；需要根据动作结果动态生成时传函数 */
  successMessage: string | (() => string)
  /** 失败提示兜底文案；省略时按单参 `fail(e)` 走（后端原文透出） */
  failMessage?: string
  /** 成功后刷新列表（分页列表传 `reload`，非分页列表传 `load`） */
  refresh?: () => Promise<unknown>
  /** 成功后关闭抽屉（写操作型动作通常要关；启停类一般不关） */
  closeDrawer?: () => void
  /** 刷新后按 id 回填抽屉数据；**composable 内已统一加 `current.id === id` 守卫语义**，传函数即可 */
  syncAfterRefresh?: (id: number) => void
}

export function useRowAction() {
  /** 正在执行动作的行 id（`null` = 无动作在途）；模板**不直接读**，经 `isBusy()` */
  const busyId = ref<number | null>(null)

  /** 模板置灰判据：`:disabled="isBusy(row.id)"` / `isBusy(current?.id)` */
  function isBusy(id?: number): boolean {
    return busyId.value !== null && busyId.value === id
  }

  async function runRowAction(opts: RowActionOptions): Promise<void> {
    busyId.value = opts.id
    try {
      await opts.action()
      ElMessage.success(
        typeof opts.successMessage === 'function' ? opts.successMessage() : opts.successMessage,
      )
      if (opts.refresh) await opts.refresh()
      if (opts.closeDrawer) opts.closeDrawer()
      if (opts.syncAfterRefresh) opts.syncAfterRefresh(opts.id)
    } catch (e) {
      if (opts.failMessage) fail(e, opts.failMessage)
      else fail(e)
    } finally {
      busyId.value = null
    }
  }

  return { busyId, isBusy, runRowAction }
}
