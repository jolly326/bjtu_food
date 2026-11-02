import { describe, expect, it } from 'vitest'

import { applyMosaicPlan, buildMosaicPlan, type TileBox } from '@/utils/mapLayout'

/** 确定性随机源（mulberry32），保证单测可复现 */
function mulberry32(seed: number): () => number {
  let s = seed
  return () => {
    s = (s + 0x6d2b79f5) | 0
    let t = Math.imul(s ^ (s >>> 15), 1 | s)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function overlaps(a: TileBox, b: TileBox): boolean {
  return (
    a.x < b.x + b.size &&
    a.x + a.size > b.x &&
    a.y < b.y + b.size &&
    a.y + a.size > b.y
  )
}

describe('mapLayout：马赛克拼贴', () => {
  it('生成与菜品数一致的落格，2×2 不重叠、不出界', () => {
    const plan = buildMosaicPlan(20, { rng: mulberry32(12345) })
    expect(plan.placements.length).toBe(20)

    const bigs = plan.placements.filter((p) => p.big).length
    expect(bigs).toBeGreaterThanOrEqual(1)
    expect(bigs).toBeLessThanOrEqual(2)

    const tile = 240
    const cell = 300
    const layout = applyMosaicPlan(plan, tile, cell)

    layout.boxes.forEach((b, i) => {
      const big = plan.placements[i]!.big
      expect(b.size).toBe(big ? tile + cell : tile)
      expect(b.x).toBeGreaterThanOrEqual(0)
      expect(b.y).toBeGreaterThanOrEqual(0)
      expect(b.x + b.size).toBeLessThanOrEqual(layout.worldW + 0.001)
      expect(b.y + b.size).toBeLessThanOrEqual(layout.worldH + 0.001)
    })

    for (let i = 0; i < layout.boxes.length; i++) {
      for (let j = i + 1; j < layout.boxes.length; j++) {
        expect(overlaps(layout.boxes[i]!, layout.boxes[j]!)).toBe(false)
      }
    }
  })

  it('相同随机源产生相同结构（可复现）', () => {
    const a = buildMosaicPlan(15, { rng: mulberry32(7) })
    const b = buildMosaicPlan(15, { rng: mulberry32(7) })
    expect(a).toEqual(b)
  })

  it('空列表返回空结构', () => {
    expect(buildMosaicPlan(0)).toEqual({ cols: 0, rows: 0, placements: [], functional: [] })
  })

  it('少于 4 道菜不放 2×2', () => {
    const plan = buildMosaicPlan(3, { rng: mulberry32(99) })
    expect(plan.placements.filter((p) => p.big).length).toBe(0)
  })

  it('必含一个日月切换功能按钮，且不与菜品/空位重叠', () => {
    const plan = buildMosaicPlan(60, { rng: mulberry32(2024) })
    expect(plan.functional).toHaveLength(1)
    const t = plan.functional[0]
    const occupied = new Set(plan.placements.map((p) => `${p.row}:${p.col}`))
    expect(occupied.has(`${t.row}:${t.col}`)).toBe(false)
  })

  it('2×2 大图数量随菜品数放大（约 12%，下限 4、上限 7）', () => {
    const plan = buildMosaicPlan(60, { rng: mulberry32(2025) })
    const big = plan.placements.filter((p) => p.big).length
    expect(big).toBeGreaterThanOrEqual(4)
    expect(big).toBeLessThanOrEqual(7)
  })

  it('每载入都包含 2×2 大图', () => {
    for (let seed = 1; seed <= 20; seed++) {
      const plan = buildMosaicPlan(60, { rng: mulberry32(seed) })
      expect(plan.placements.some((p) => p.big)).toBe(true)
    }
  })

  it('多轮随机均保持不重叠', () => {
    const tile = 240
    const cell = 300
    for (let seed = 1; seed <= 30; seed++) {
      const plan = buildMosaicPlan(18, { rng: mulberry32(seed) })
      const layout = applyMosaicPlan(plan, tile, cell)
      for (let i = 0; i < layout.boxes.length; i++) {
        for (let j = i + 1; j < layout.boxes.length; j++) {
          expect(overlaps(layout.boxes[i]!, layout.boxes[j]!)).toBe(false)
        }
      }
    }
  })
})
