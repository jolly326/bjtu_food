import { describe, expect, it } from 'vitest'

import { freshBatch } from '@/utils/dishBatch'

const card = (id: number) => ({ id })

describe('freshBatch：随机盲选排除当前批次', () => {
  it('过滤掉当前批次已出现的菜品，保留原顺序', () => {
    const seen = [card(1), card(2)]
    const records = [card(2), card(3), card(1), card(4)]
    expect(freshBatch(records, seen).map((c) => c.id)).toEqual([3, 4])
  })

  it('过滤后为空 ⇒ 退回未过滤结果（不出现空批次）', () => {
    const seen = [card(1), card(2)]
    const records = [card(1), card(2)]
    expect(freshBatch(records, seen).map((c) => c.id)).toEqual([1, 2])
  })

  it('无当前批次（首次加载）⇒ 原样返回', () => {
    const records = [card(1), card(2)]
    expect(freshBatch(records, [])).toEqual(records)
  })
})
