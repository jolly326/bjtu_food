import { describe, expect, it } from 'vitest'

import { cooldownFrom, cooldownLeft, markCooldown, parseRetrySeconds } from '@/utils/backoff'

/**
 * 429 退避：等待秒数取自服务端文案，退避按入口（key）分别计数。
 * 口径见 docs/secur/S-03 §二。
 */

describe('退避：等待秒数', () => {
  it('文案含秒数时取该秒数，无秒数时为 0', () => {
    expect(parseRetrySeconds('操作过于频繁，请 60 秒后再试')).toBe(60)
    expect(parseRetrySeconds('操作过于频繁，请稍后再试')).toBe(0)
  })
})

describe('退避：按入口计数', () => {
  it('登记的入口不可用，其它入口不受影响', () => {
    markCooldown('test:a', 30)
    expect(cooldownLeft('test:a')).toBeGreaterThan(0)
    expect(cooldownLeft('test:b')).toBe(0)
  })

  it('仅 429 登记退避；其它业务码不禁用入口', () => {
    const limited = Object.assign(new Error('操作过于频繁，请 30 秒后再试'), { code: 429 })
    expect(cooldownFrom('test:c', limited)).toBe(30)
    expect(cooldownLeft('test:c')).toBeGreaterThan(0)

    const invalid = Object.assign(new Error('参数不合法'), { code: 400 })
    expect(cooldownFrom('test:d', invalid)).toBe(0)
    expect(cooldownLeft('test:d')).toBe(0)
  })
})