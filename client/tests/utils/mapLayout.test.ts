import { describe, expect, it } from 'vitest'

import {
  buildGridIndex,
  buildStaggeredLayout,
  cullTiles,
  hitTestGrid,
} from '@/utils/mapLayout'

describe('mapLayout：错位方格', () => {
  it('按奇偶行错位半格，并给出世界尺寸', () => {
    const layout = buildStaggeredLayout(5, 200, 330)

    expect(layout.cols).toBe(3)
    expect(layout.rows).toBe(2)
    expect(layout.worldW).toBe(3 * 330 + 165)
    expect(layout.worldH).toBe(2 * 330)

    expect(layout.boxes[0]).toEqual({ x: 65, y: 65, size: 200 })
    expect(layout.boxes[1]).toEqual({ x: 395, y: 65, size: 200 })
    // 第 2 行整体右移半格
    expect(layout.boxes[3]).toEqual({ x: 230, y: 395, size: 200 })
  })

  it('空列表返回空世界', () => {
    expect(buildStaggeredLayout(0, 200, 330)).toEqual({
      boxes: [],
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
