import { describe, it, expect } from 'vitest'
import { FLOOR_OPTIONS } from '@/pages/correction/useCorrection'

/**
 * 楼层受控字典回归测试。
 *
 * <p>字典**值即汉字**（存储值 = 显示值）——端上零映射、零兜底，故唯一需要钉死的是
 * 「字典项与 `docs/schema/stall.md` 完全一致且无重复」：一旦漂移，提交值会被服务端
 * 的楼层字典校验（字典外 → 400）拒绝。
 */
describe('FLOOR_OPTIONS（楼层受控字典 · 值即汉字）', () => {
  it('与 schema/stall.md 的楼层字典逐项一致、顺序稳定', () => {
    expect(FLOOR_OPTIONS).toEqual(['负一层', '一层', '二层', '三层', '四层'])
  })

  it('字典项唯一（否则弹层会出现重复 / 不可达行）', () => {
    expect(new Set(FLOOR_OPTIONS).size).toBe(FLOOR_OPTIONS.length)
  })
})
