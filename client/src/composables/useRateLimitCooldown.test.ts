import { RateLimitedError } from '@/api/errors'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

/**
 * 限频退避回归测试。
 *
 * <p>最关键的两条边界：**下限 1 秒**（后端返 0 不得让用户立即再撞）、
 * **上限 300 秒**（异常文案「请 99999 秒」不得把 UI 锁死）。
 * 另：解析不出秒数时用保守缺省 30 秒（宁可多等）。
 *
 * <p>`useRateLimitCooldown` 内部调 `onUnmounted`，在 node 环境（无组件实例）
 * 会告警，故 mock 掉生命周期钩子。
 */
const unmounted: Array<() => void> = []
vi.mock('vue', async (orig) => {
  const actual = await orig<typeof import('vue')>()
  return { ...actual, onUnmounted: (fn: () => void) => { unmounted.push(fn) } }
})

// isRateLimited 需构造「限频异常」，用后端 message 作为判据（见 api/http）
const { useRateLimitCooldown } = await import('./useRateLimitCooldown')


/**
 * 构造限频异常。必须用**真实类**：`isRateLimited` 判据是 `e instanceof
 * RateLimitedError`（不是鸭子类型），用普通 Error 挂上 retryAfterSeconds
 * 属性无法通过识别。
 */
function rateLimitErr(seconds?: number): Error {
  return new RateLimitedError(
    seconds != null ? `请求过于频繁，请 ${seconds} 秒后再试` : '请求过于频繁',
    seconds,
  )
}

beforeEach(() => {
  vi.useFakeTimers()
  unmounted.length = 0
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useRateLimitCooldown · 初次状态', () => {
  it('初始不在退避期', () => {
    const c = useRateLimitCooldown()
    expect(c.cooldownSeconds.value).toBe(0)
    expect(c.cooling()).toBe(false)
  })

  it('非限频异常不进倒计时，且返回 false（调用方据此自己弹兜底提示）', () => {
    const c = useRateLimitCooldown()
    expect(c.handleError(new Error('普通业务失败'))).toBe(false)
    expect(c.cooldownSeconds.value).toBe(0)
  })
})

describe('useRateLimitCooldown · 退避时长钳制', () => {
  it('解析不出秒数时用保守缺省 30 秒', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(undefined))
    expect(c.cooldownSeconds.value).toBe(30)
  })

  it('下限 1 秒（后端返 0 不得让用户立即再撞限频）', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(0))
    expect(c.cooldownSeconds.value).toBe(1)
  })

  it('负数归到下限 1 秒', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(-10))
    expect(c.cooldownSeconds.value).toBe(1)
  })

  it('上限 300 秒（异常文案不得把 UI 锁死）', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(99999))
    expect(c.cooldownSeconds.value).toBe(300)
  })

  it('小数四舍五入', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(4.6))
    expect(c.cooldownSeconds.value).toBe(5)
  })

  it('正常值原样采用', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(12))
    expect(c.cooldownSeconds.value).toBe(12)
  })
})

describe('useRateLimitCooldown · 倒计时', () => {
  it('每秒递减，到 0 自动停止（不空转）', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(3))
    expect(c.cooldownSeconds.value).toBe(3)

    vi.advanceTimersByTime(1000)
    expect(c.cooldownSeconds.value).toBe(2)
    vi.advanceTimersByTime(2000)
    expect(c.cooldownSeconds.value).toBe(0)
    expect(c.cooling()).toBe(false)

    // 归零后继续推进时间不应变成负数
    vi.advanceTimersByTime(5000)
    expect(c.cooldownSeconds.value).toBe(0)
  })

  it('clearCooldown 清零并停表（提交成功后调用）', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(10))
    c.clearCooldown()
    expect(c.cooldownSeconds.value).toBe(0)

    vi.advanceTimersByTime(3000)
    expect(c.cooldownSeconds.value).toBe(0)
  })

  it('重复触发限频时重新计时，不叠加（timer 被重启）', () => {
    const c = useRateLimitCooldown()
    c.handleError(rateLimitErr(10))
    vi.advanceTimersByTime(3000)
    expect(c.cooldownSeconds.value).toBe(7)

    c.handleError(rateLimitErr(5))
    expect(c.cooldownSeconds.value).toBe(5)
    vi.advanceTimersByTime(5000)
    expect(c.cooldownSeconds.value).toBe(0)
  })
})
