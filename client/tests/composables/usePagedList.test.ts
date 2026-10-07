import { describe, it, expect } from 'vitest'
import { mergePagedRows } from '@/composables/usePagedList'
import { RESULT_PAGE_SIZE } from '@/constants/paging'

describe('mergePagedRows', () => {
  it('0 长度即封口到底，且不触碰 current（避免空态闪现）', () => {
    const current = [{ id: 1 }, { id: 2 }]
    const r = mergePagedRows(current, [], RESULT_PAGE_SIZE)
    expect(r.finished).toBe(true)
    // 关键：返回原数组引用，不产生新的空列表
    expect(r.rows).toBe(current)
    expect(r.rows).toHaveLength(2)
  })

  it('满页未封底（后端无 total，满页不能判定还有下一页）', () => {
    const incoming = Array.from({ length: RESULT_PAGE_SIZE }, (_, i) => ({ id: i + 100 }))
    const r = mergePagedRows([{ id: 1 }], incoming, RESULT_PAGE_SIZE)
    expect(r.finished).toBe(false)
    expect(r.rows).toHaveLength(RESULT_PAGE_SIZE + 1)
  })

  it('不足一页即封底', () => {
    const r = mergePagedRows([{ id: 1 }], [{ id: 2 }, { id: 3 }], RESULT_PAGE_SIZE)
    expect(r.finished).toBe(true)
    expect(r.rows).toHaveLength(3)
  })

  it('按 id 去重追加（防分页跳号导致重复行）', () => {
    const r = mergePagedRows(
      [{ id: 1 }, { id: 2 }],
      [{ id: 2 }, { id: 3 }],
      RESULT_PAGE_SIZE,
    )
    expect(r.rows.map((x) => x.id)).toEqual([1, 2, 3])
  })

  it('去重后即使本页满页也不封底 —— 跳号情形下可能仍有权��', () => {
    // incoming 满页但全部重复：mergePagedRows 不据此封底（封底判据只看 incoming 长度）
    const incoming = Array.from({ length: RESULT_PAGE_SIZE }, (_, i) => ({ id: i + 1 }))
    const r = mergePagedRows(Array.from({ length: RESULT_PAGE_SIZE }, (_, i) => ({ id: i + 1 })), incoming, RESULT_PAGE_SIZE)
    expect(r.rows).toHaveLength(RESULT_PAGE_SIZE)
    expect(r.finished).toBe(false)
  })
})