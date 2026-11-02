import { describe, expect, it } from 'vitest'

import {
  buildGridIndex,
  buildGridLayout,
  cullTiles,
  hitTestGrid,
  pickScatteredHoles,
} from '@/utils/mapLayout'

describe('mapLayout：规整网格', () => {
  it('逐行铺满、行间不错位，并给出世界尺寸', () => {
    const layout = buildGridLayout(5, 200, 330)

    // 5 个：2×3 比 3×2 更接近正方形
    expect(layout.cols).toBe(2)
    expect(layout.rows).toBe(3)
    expect(layout.worldW).toBe(2 * 330)
    expect(layout.worldH).toBe(3 * 330)

    expect(layout.boxes[0]).toEqual({ x: 65, y: 65, size: 200 })
    expect(layout.boxes[1]).toEqual({ x: 395, y: 65, size: 200 })
    // 第 2 行与第 1 行左对齐（无半格错位）
    expect(layout.boxes[2]).toEqual({ x: 65, y: 395, size: 200 })
  })

  it('整体趋近正方形', () => {
    // 18 个：4×5（比 6×3 更方）
    const l18 = buildGridLayout(18, 200, 330)
    expect(l18.cols).toBe(4)
    expect(l18.rows).toBe(5)

    // 13 个：4×4
    const l13 = buildGridLayout(13, 200, 330)
    expect(l13.cols).toBe(4)
    expect(l13.rows).toBe(4)

    // 9 个：3×3
    const l9 = buildGridLayout(9, 200, 330)
    expect(l9.cols).toBe(3)
    expect(l9.rows).toBe(3)
  })

  it('空列表返回空世界', () => {
    expect(buildGridLayout(0, 200, 330)).toEqual({
      boxes: [],
      functional: [],
      worldW: 0,
      worldH: 0,
      cols: 0,
      rows: 0,
    })
  })
})

describe('mapLayout：网格命中', () => {
  const tiles = [{ id: 1, x: 0, y: 0, size: 100 }]

  it('命中格内坐标', () => {
    const index = buildGridIndex(tiles, 100)
    expect(hitTestGrid(index, 50, 50)?.id).toBe(1)
    expect(hitTestGrid(index, 0, 0)?.id).toBe(1)
  })

  it('格内空白处不命中', () => {
    const index = buildGridIndex(tiles, 100)
    expect(hitTestGrid(index, 300, 300)).toBeNull()
  })
})

describe('mapLayout：视口裁剪', () => {
  it('只保留视口外扩半屏内的磁贴', () => {
    const tiles = [
      { id: 1, x: 0, y: 0, size: 100 },
      { id: 2, x: 2000, y: 2000, size: 100 },
    ]
    const visible = cullTiles(tiles, { width: 300, height: 300, tx: 0, ty: 0, scale: 1 })
    expect(visible.map((t) => t.id)).toEqual([1])
  })
})

describe('mapLayout：散布空位', () => {
  it('pickScatteredHoles 挑选的空位互不相邻（8 邻域：切比雪夫距离 ≥ 2）', () => {
    let s = 123456789
    const rng = () => {
      s = (s * 1103515245 + 12345) & 0x7fffffff
      return s / 0x7fffffff
    }
    // 12×10 网格的 120 个空闲格
    const free = Array.from({ length: 120 }, (_, i) => ({ r: Math.floor(i / 12), c: i % 12 }))
    for (let t = 0; t < 60; t++) {
      const holes = pickScatteredHoles(free, 9, rng)
      const cells = [...holes].map((i) => free[i])
      for (let i = 0; i < cells.length; i++) {
        for (let j = i + 1; j < cells.length; j++) {
          const dr = Math.abs(cells[i].r - cells[j].r)
          const dc = Math.abs(cells[i].c - cells[j].c)
          // 8 邻域不相邻 = 任意两空位切比雪夫距离 ≥ 2
          expect(Math.max(dr, dc)).toBeGreaterThan(1)
        }
      }
    }
  })
})
