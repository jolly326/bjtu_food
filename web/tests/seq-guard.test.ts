import { describe, it, expect, beforeEach } from 'vitest'
import { createSeqGuard } from '@/utils/seq-guard'

/**
 * `createSeqGuard` 是列表页并发正确性的核心：它决定「哪一次请求的响应可以落到界面上」。
 * 语义是**丢弃过期响应**（每次请求都发出、返回时按新旧裁决），
 * 而不是「loading 时丢弃后一次请求」（后者会让用户最后一次筛选变更被静默忽略）。
 */
describe('createSeqGuard · 新旧裁决', () => {
  it('首次发起即当前，响应可采纳', () => {
    const g = createSeqGuard()
    expect(g.isCurrent(g.begin())).toBe(true)
  })

  it('后发起的请求生效后，先发起的响应被判为过期', () => {
    const g = createSeqGuard()
    const first = g.begin()
    const second = g.begin()
    expect(g.isCurrent(first)).toBe(false)
    expect(g.isCurrent(second)).toBe(true)
  })

  it('并发三个请求：只有最后一次的响应可采纳（快速连点切筛选的正确语义）', () => {
    const g = createSeqGuard()
    const a = g.begin()
    const b = g.begin()
    const c = g.begin()
    expect([g.isCurrent(a), g.isCurrent(b), g.isCurrent(c)]).toEqual([false, false, true])
  })

  it('peek 读当前号但不取号：不误杀在途请求', () => {
    const g = createSeqGuard()
    g.begin()
    expect(g.peek()).toBe(1)
    expect(g.peek()).toBe(1)
    expect(g.isCurrent(1)).toBe(true)
  })

  it('invalidate 作废所有在途请求（旧号失效，重新取号后生效）', () => {
    const g = createSeqGuard()
    const old = g.begin()
    g.invalidate()
    expect(g.isCurrent(old)).toBe(false)
    expect(g.isCurrent(g.begin())).toBe(true)
  })
})