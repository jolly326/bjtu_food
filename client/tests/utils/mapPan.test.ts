import { describe, expect, it } from 'vitest'
import { computePanBounds } from '@/utils/mapPan'

describe('computePanBounds：边界以「最边缘卡片中心可达视口中心」为准', () => {
  it('世界大于视口 ⇒ 首 / 末卡片都能被拖到视口中心', () => {
    const b = computePanBounds(2000, 1000, 1, 800, 600, 100)
    expect(b.maxX).toBe(300) // 400 - 100
    expect(b.minX).toBe(-1500) // 400 - (2000 - 100)
    expect(b.maxY).toBe(200) // 300 - 100
    expect(b.minY).toBe(-600) // 300 - (1000 - 100)
  })

  it('世界小于视口 ⇒ 仍可拖动，不再锁死居中', () => {
    const b = computePanBounds(500, 400, 1, 800, 600, 100)
    expect(b.minX).toBe(0) // 400 - (500 - 100)
    expect(b.maxX).toBe(300) // 400 - 100
    expect(b.minY).toBe(0) // 300 - (400 - 100)
    expect(b.maxY).toBe(200) // 300 - 100
  })

  it('单张卡片（世界边长 = 2 × edgeInset）⇒ 两侧收敛为同一点，自动居中', () => {
    const b = computePanBounds(200, 200, 1, 800, 600, 100)
    expect(b.minX).toBe(300)
    expect(b.maxX).toBe(300)
    expect(b.minY).toBe(200)
    expect(b.maxY).toBe(200)
  })

  it('放大后边界随缩放同步扩大', () => {
    const b = computePanBounds(1000, 1000, 2, 800, 600, 50)
    expect(b.maxX).toBe(300) // 400 - 50 × 2
    expect(b.minX).toBe(-1500) // 400 - (2000 - 100)
    expect(b.maxY).toBe(200) // 300 - 100
    expect(b.minY).toBe(-1600) // 300 - (2000 - 100)
  })

  it('未传 edgeInset ⇒ 退化为「世界边缘可到视口中心」', () => {
    const b = computePanBounds(2000, 1000, 1, 800, 600)
    expect(b.maxX).toBe(400)
    expect(b.minX).toBe(-1600)
    expect(b.maxY).toBe(300)
    expect(b.minY).toBe(-700)
  })
})
