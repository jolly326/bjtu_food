import { describe, expect, it } from 'vitest'
import {
  closeImageZoom,
  imageZoom,
  openImageZoom,
  toggleImageZoom,
  zoomImageZoom,
} from '@/composables/useImageZoom'

describe('useImageZoom', () => {
  it('open 载入目标图并重置缩放 / 平移', () => {
    imageZoom.scale = 3
    imageZoom.tx = 50
    imageZoom.ty = 20
    openImageZoom('https://x/a.png', '红烧肉')
    expect(imageZoom.open).toBe(true)
    expect(imageZoom.src).toBe('https://x/a.png')
    expect(imageZoom.alt).toBe('红烧肉')
    expect(imageZoom.scale).toBe(1)
    expect(imageZoom.tx).toBe(0)
    expect(imageZoom.ty).toBe(0)
  })

  it('滚轮缩放夹在 [1, 4]，回到 1x 时重置平移', () => {
    openImageZoom('u')
    imageZoom.tx = 30
    zoomImageZoom(-100) // 放大一步
    expect(imageZoom.scale).toBeGreaterThan(1)
    for (let i = 0; i < 50; i++) zoomImageZoom(-100)
    expect(imageZoom.scale).toBe(4) // 封顶 4x
    for (let i = 0; i < 50; i++) zoomImageZoom(100)
    expect(imageZoom.scale).toBe(1) // 触底 1x
    expect(imageZoom.tx).toBe(0) // 回 1x 清平移
  })

  it('点击在 1x 与 2x 间切换', () => {
    openImageZoom('u')
    expect(imageZoom.scale).toBe(1)
    toggleImageZoom()
    expect(imageZoom.scale).toBe(2)
    toggleImageZoom()
    expect(imageZoom.scale).toBe(1)
  })

  it('close 关闭预览', () => {
    openImageZoom('u')
    closeImageZoom()
    expect(imageZoom.open).toBe(false)
  })
})
