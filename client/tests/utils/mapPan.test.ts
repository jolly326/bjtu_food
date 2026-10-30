import { describe, expect, it } from 'vitest'
import { computePanBounds } from '@/utils/mapPan'

describe('computePanBounds：世界小于视口 ⇒ 居中、不可拖动', () => {
  it('min 与 max 同为居中偏移（横纵分别居中）', () => {
    const b = computePanBounds(500, 400, 1, 800, 600)
    expect(b.minX).toBe(150)
    expect(b.maxX).toBe(150)
    expect(b.minY).toBe(100)
    expect(b.maxY).toBe(100)
  })

  it('恰好等于视口 ⇒ 偏移为 0', () => {
    const b = computePanBounds(800, 600, 1, 800, 600)
    expect(b.minX).toBe(0)
    expect(b.maxX).toBe(0)
    expect(b.minY).toBe(0)
    expect(b.maxY).toBe(0)
  })
})

describe('computePanBounds：世界大于视口 ⇒ 可拖动，范围 [视口 - 世界, 0]', () => {
  it('未缩放时边界为负值到 0', () => {
    const b = computePanBounds(2000, 1000, 1, 800, 600)
    expect(b.minX).toBe(-1200)
    expect(b.maxX).toBe(0)
    expect(b.minY).toBe(-400)
    expect(b.maxY).toBe(0)
  })

  it('缩小到小于视口后回到居中（跨临界切换）', () => {
    const b = computePanBounds(2000, 1000, 0.2, 800, 600)
    expect(b.minX).toBe(200)
    expect(b.maxX).toBe(200)
    expect(b.minY).toBe(200)
    expect(b.maxY).toBe(200)
  })

  it('放大后可拖动范围随缩放增大', () => {
    const b = computePanBounds(1000, 1000, 2, 800, 600)
    expect(b.minX).toBe(-1200)
    expect(b.maxX).toBe(0)
    expect(b.minY).toBe(-1400)
    expect(b.maxY).toBe(0)
  })
})
