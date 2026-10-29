import { afterEach, describe, expect, it, vi } from 'vitest'

import { MAP } from '@/constants'
import {
  cancelFrame,
  clamp,
  prefersReducedMotion,
  requestFrame,
  shuffled,
} from '@/utils/mapMotion'
import { rpxToPx } from '@/utils/viewport'

describe('mapMotion：区间收敛与洗牌', () => {
  it('clamp 把值收敛进区间（含端点）', () => {
    expect(clamp(5, 0, 10)).toBe(5)
    expect(clamp(-1, 0, 10)).toBe(0)
    expect(clamp(11, 0, 10)).toBe(10)
  })

  it('shuffled 打乱顺序但不增减元素（元素构成不变）', () => {
    const source = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']
    const out = shuffled(source)

    expect(out).toHaveLength(source.length)
    expect([...out].sort()).toEqual([...source].sort())
    // 入参不被改动（差一份副本）
    expect(source).toEqual(['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'])
  })
})

describe('mapMotion：动效降级', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('系统未开启减少动效 ⇒ false', () => {
    vi.stubGlobal('wx', { getSystemSetting: () => ({ reduceMotion: false }) })
    expect(prefersReducedMotion()).toBe(false)
  })

  it('系统开启减少动效 ⇒ true（惯性 / 弹性将被跳过）', () => {
    vi.stubGlobal('wx', { getSystemSetting: () => ({ reduceMotion: true }) })
    expect(prefersReducedMotion()).toBe(true)
  })

  it('接口缺失或抛错 ⇒ 回落 false（不阻断手势）', () => {
    vi.stubGlobal('wx', {})
    expect(prefersReducedMotion()).toBe(false)

    vi.stubGlobal('wx', {
      getSystemSetting: () => {
        throw new Error('不支持')
      },
    })
    expect(prefersReducedMotion()).toBe(false)
  })
})

describe('mapMotion：帧调度', () => {
  it('无 rAF 环境回退 setTimeout，并可被取消', () => {
    vi.useFakeTimers()
    const callback = vi.fn()
    const frames = vi.fn()
    vi.stubGlobal('requestAnimationFrame', undefined)

    const handle = requestFrame(callback)
    expect(typeof handle).not.toBe('number')

    cancelFrame(handle)
    vi.runAllTimers()
    expect(callback).not.toHaveBeenCalled()
    frames.mockRestore()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })
})

describe('viewport：rpx 换算', () => {
  it('750rpx = 屏宽（按窗口宽等比缩放）', () => {
    expect(rpxToPx(750, 375)).toBe(375)
    expect(rpxToPx(MAP.TILE_RPX, 375)).toBe(120)
    expect(rpxToPx(MAP.CELL_RPX, 750)).toBe(300)
  })
})
