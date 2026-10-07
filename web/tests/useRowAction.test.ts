import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { installDomStub } from './support/dom-stub'

/**
 * `useRowAction` 是全部列表页行内动作的并发骨架。本用例锁定**执行顺序**与**锁的释放**：
 * `置锁 → action() → 成功提示 → refresh() → closeDrawer() → 回填`，
 * 且无论成功失败都要在 finally 里把锁清掉（否则该行按钮永久置灰）。
 *
 * `ElMessage` / `fail` 走 mock：这两者是全局副作用，不属于本用例的断言对象。
 */
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() } }))
vi.mock('@/utils/error', () => ({ fail: vi.fn() }))

import { ElMessage } from 'element-plus'
import { fail } from '@/utils/error'
import { useRowAction } from '@/composables/useRowAction'

let uninstall: () => void

beforeEach(() => {
  uninstall = installDomStub()
  vi.clearAllMocks()
})

afterEach(() => uninstall())

describe('useRowAction · 执行顺序', () => {
  it('成功：按 提示 → 刷新 → 关抽屉 → 回填 的固定顺序执行', async () => {
    const order: string[] = []
    const { runRowAction } = useRowAction()
    await runRowAction({
      id: 7,
      action: async () => {
        order.push('action')
      },
      successMessage: '已启用',
      refresh: async () => {
        order.push('refresh')
      },
      closeDrawer: () => order.push('close'),
      syncAfterRefresh: (id) => order.push('sync:' + id),
    })
    expect(order).toEqual(['action', 'refresh', 'close', 'sync:7'])
    expect(ElMessage.success).toHaveBeenCalledWith('已启用')
  })

  it('成功文案支持按结果动态生成（传函数）', async () => {
    const { runRowAction } = useRowAction()
    let enabled = false
    await runRowAction({
      id: 1,
      action: async () => {
        enabled = true
      },
      successMessage: () => (enabled ? '已启用' : '已停用'),
    })
    expect(ElMessage.success).toHaveBeenCalledWith('已启用')
  })

  it('可省略 refresh / closeDrawer / 回填三步（不传即不执行）', async () => {
    const { runRowAction } = useRowAction()
    await expect(
      runRowAction({ id: 1, action: async () => undefined, successMessage: '已删除' }),
    ).resolves.toBeUndefined()
    expect(ElMessage.success).toHaveBeenCalledWith('已删除')
  })
})

describe('useRowAction · 并发锁', () => {
  it('执行中该行置灰，结束后释放', async () => {
    const { isBusy, runRowAction } = useRowAction()
    let seenDuring = false
    await runRowAction({
      id: 42,
      action: async () => {
        seenDuring = isBusy(42)
      },
      successMessage: 'ok',
    })
    expect(seenDuring).toBe(true)
    expect(isBusy(42)).toBe(false)
    expect(isBusy(undefined)).toBe(false)
  })

  it('失败也必定释放锁（不清锁会让该行按钮永久置灰）', async () => {
    const { isBusy, runRowAction } = useRowAction()
    await runRowAction({
      id: 9,
      action: async () => {
        throw new Error('boom')
      },
      successMessage: 'ok',
    })
    expect(isBusy(9)).toBe(false)
  })

  it('失败时按 failMessage 兜底；省略则单参透出后端原文', async () => {
    const { runRowAction } = useRowAction()
    const e = new Error('后端原文')
    await runRowAction({ id: 1, action: async () => { throw e }, successMessage: 'x', failMessage: '删除失败' })
    expect(fail).toHaveBeenCalledWith(e, '删除失败')

    vi.clearAllMocks()
    await runRowAction({ id: 1, action: async () => { throw e }, successMessage: 'x' })
    expect(fail).toHaveBeenCalledWith(e)
  })

  it('动作被取消时（action 抛错）不提示成功、不刷新', async () => {
    const { runRowAction } = useRowAction()
    const refresh = vi.fn()
    await runRowAction({
      id: 1,
      action: async () => {
        throw new Error('cancel')
      },
      successMessage: 'ok',
      refresh,
    })
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(refresh).not.toHaveBeenCalled()
  })
})