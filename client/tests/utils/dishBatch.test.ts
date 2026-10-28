import { describe, expect, it } from 'vitest'

import { uniqueById } from '@/utils/dishBatch'

const dish = (id: number) => ({ id })

describe('uniqueById：美食地图铺放前按 id 去重', () => {
  it('保留首次出现的顺序，丢弃重复 id', () => {
    expect(uniqueById([dish(2), dish(1), dish(2), dish(3), dish(1)]).map((d) => d.id)).toEqual([
      2, 1, 3,
    ])
  })

  it('空列表原样返回', () => {
    expect(uniqueById([])).toEqual([])
  })

  it('无重复时保持原顺序', () => {
    const list = [dish(3), dish(1), dish(2)]
    expect(uniqueById(list)).toEqual(list)
  })
})
