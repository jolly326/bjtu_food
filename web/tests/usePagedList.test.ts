import { describe, it, expect } from 'vitest'
import { usePagedList } from '@/composables/usePagedList'

/**
 * `usePagedList.reload()` 是删除 / 处置后「保持筛选与页码」的刷新入口。
 * 本用例锁定**移除行后的页码回退**：末页删空后必须落到仍有数据的一页 ——
 * 既不能停在「空列表 + 上一页可点」的越界页，也不能为省事跳回第 1 页（丢失浏览位置）。
 *
 * 服务端以「按页切片 + `total`」模拟：越界页返回空记录，与真实分页接口同形。
 * 定位到末页用 `load(2)` 而非 `nextPage()`：`goToPage` 带 `window.scrollTo`，
 * 与页码回退无关，不属本用例的断言对象。
 */
describe('usePagedList · 移除行后的页码回退', () => {
  it('末页删掉最后一条 ⇒ 回退到最后一页（不停在空页）', async () => {
    const { list, requestedPages, remove } = setup([1, 2, 3], 2)
    await list.load(2) // 第 2 页（末页，仅 [3]）
    expect(list.items.value).toEqual([3])
    expect(list.page.value).toBe(2)

    remove(3)
    requestedPages.length = 0
    await list.reload()

    // 总页数收缩为 1 ⇒ 回退到第 1 页（= 新的最后一页），列表不再为空
    expect(list.page.value).toBe(1)
    expect(list.items.value).toEqual([1, 2])
    expect(requestedPages).toEqual([2, 1])
  })

  it('整表删空 ⇒ 回到第 1 页（不留在越界页）', async () => {
    const { list, remove } = setup([1, 2, 3], 2)
    await list.load(2)
    expect(list.page.value).toBe(2)

    remove(1)
    remove(2)
    remove(3)
    await list.reload()

    expect(list.total.value).toBe(0)
    expect(list.page.value).toBe(1)
    expect(list.items.value).toEqual([])
  })

  it('本页被删空但页码未越界 ⇒ 回退上一页（服务端 total 滞后于记录时的兜底）', async () => {
    const { list, requestedPages, removeKeepTotal } = setup([1, 2, 3, 4], 2)
    await list.load(2) // 第 2 页 = [3, 4]
    expect(list.items.value).toEqual([3, 4])

    // 末页两条已被移除，但服务端回的 `total` 仍是 4 ⇒ pageCount 仍为 2，越界分支不成立
    removeKeepTotal(3)
    removeKeepTotal(4)
    requestedPages.length = 0
    await list.reload()

    expect(list.page.value).toBe(1)
    expect(list.items.value).toEqual([1, 2])
    expect(requestedPages).toEqual([2, 1])
  })

  it('未移除行时 reload 只刷新当前页（不额外发请求、不跳页）', async () => {
    const { list, requestedPages } = setup([1, 2, 3], 2)
    await list.load(2)
    expect(list.page.value).toBe(2)

    requestedPages.length = 0
    await list.reload()

    expect(requestedPages).toEqual([2])
    expect(list.page.value).toBe(2)
    expect(list.items.value).toEqual([3])
  })
})

/**
 * 测试脚手架：按页切片的分页服务端 + 可变的「库内数据」。
 *
 * @param initial 初始数据（数组顺序即列表顺序）
 * @param pageSize 每页条数（线上固定 20；用例内用小值构造末页边界）
 */
function setup(initial: number[], pageSize = 20) {
  let rows = [...initial]
  /** 服务端回的 `total`（`removeKeepTotal` 场景下不随 `rows` 收缩） */
  let total = rows.length
  /** 每次请求的目标页号（断言「是否多补发了一次请求」的依据） */
  const requestedPages: number[] = []

  const list = usePagedList<number>(async (page, size) => {
    requestedPages.push(page)
    const start = (page - 1) * size
    return { records: rows.slice(start, start + size), total }
  }, pageSize)

  return {
    list,
    requestedPages,
    /** 删除一行（记录与 `total` 同步收缩 —— 常规删除） */
    remove: (id: number) => {
      rows = rows.filter((r) => r !== id)
      total = rows.length
    },
    /** 删除一行但 `total` 保持原值 —— 模拟服务端分页计数滞后于数据 */
    removeKeepTotal: (id: number) => {
      rows = rows.filter((r) => r !== id)
    },
  }
}
