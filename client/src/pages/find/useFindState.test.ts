import { describe, it, expect, vi, beforeEach } from 'vitest'

/**
 * `useFindState`（搜索页薄壳的编排层）回归测试。
 *
 * <p>本模块自身逻辑很薄，价值在于**接线**：它把「搜索记录」与「结果态」两模块
 * 按业务规则编排起来。这些规则写在单个模块内部看不出来，只有跨模块才成立：
 * ① 输入提交要写搜索记录；
 * ② 点历史词**不**重复置顶（已在顶部则不动）；
 * ③ 点猜你喜欢词要写记录（与点历史词不同）；
 * ④ 清空关键词要退出结果态**并重读记录**。
 */
const store = new Map<string, unknown>()

beforeEach(() => {
  store.clear()
  ;(globalThis as Record<string, unknown>).uni = {
    getStorageSync: (k: string) => store.get(k),
    setStorageSync: (k: string, v: unknown) => {
      store.set(k, v)
    },
    showModal: vi.fn(),
  }
})

const searchMock = vi.fn()
vi.mock('@/stores/dish', () => ({
  useDishStore: () => ({ search: searchMock }),
}))

const { useFindState } = await import('./useFindState')

beforeEach(() => {
  searchMock.mockReset()
  searchMock.mockResolvedValue([])
  // 被测代码用 console.error 记录搜索失败；本文件含失败路径用例，静音以免污染报告
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('useFindState · 跨态编排', () => {
  it('输入提交：进入结果态**并**写入搜索记录', async () => {
    const s = useFindState()
    s.keyword.value = '红烧肉'
    await s.onSearchConfirm()

    expect(s.inFilter.value).toBe(true)
    expect(searchMock).toHaveBeenCalledTimes(1)
    expect(s.historyList.value).toEqual(['红烧肉'])
  })

  it('空关键词提交：不发请求、不入记录、不进结果态', async () => {
    const s = useFindState()
    s.keyword.value = '   '
    await s.onSearchConfirm()

    expect(searchMock).not.toHaveBeenCalled()
    expect(s.historyList.value).toEqual([])
    expect(s.inFilter.value).toBe(false)
  })

  it('点猜你喜欢词（record=true）：进入结果态并写记录', async () => {
    const s = useFindState()
    await s.tapKeyword('麻辣香锅', true)

    expect(s.inFilter.value).toBe(true)
    expect(s.historyList.value).toEqual(['麻辣香锅'])
  })

  it('点历史词（record=false）：进入结果态但**不**写入记录', async () => {
    const s = useFindState()
    s.historyList.value = ['红烧肉']
    await s.tapKeyword('红烧肉')

    expect(s.inFilter.value).toBe(true)
    // 点历史词不应把它重复置顶（记录已存在，顺序保持）
    expect(s.historyList.value).toEqual(['红烧肉'])
  })

  it('点历史词不会因置顶而改变已有顺序', async () => {
    const s = useFindState()
    s.historyList.value = ['a', 'b', 'c']
    await s.tapKeyword('c')
    // 「点已记录词」record=false ⇒ 不动记录顺序
    expect(s.historyList.value).toEqual(['a', 'b', 'c'])
  })

  it('keyword 被 tapKeyword 同步为所点词（供搜索框回显）', async () => {
    const s = useFindState()
    await s.tapKeyword('麻辣香锅')
    expect(s.keyword.value).toBe('麻辣香锅')
  })
})

describe('useFindState · clearKeyword（= 重新开始）', () => {
  it('清空关键词并退出结果态', async () => {
    searchMock.mockResolvedValueOnce([{ id: 1, name: '红烧肉', price: 1 }])
    const s = useFindState()
    s.keyword.value = '红烧肉'
    await s.onSearchConfirm()
    expect(s.inFilter.value).toBe(true)

    s.clearKeyword()

    expect(s.keyword.value).toBe('')
    expect(s.inFilter.value).toBe(false)
    expect(s.mixedResults.value).toEqual([])
  })

  it('退出结果态时**重读**搜索记录（以存储为真源）', async () => {
    searchMock.mockResolvedValueOnce([])
    const s = useFindState()
    s.keyword.value = '红烧肉'
    await s.onSearchConfirm()
    expect(s.historyList.value).toEqual(['红烧肉'])

    // 模拟页面栈缓存期间别处改了存储（用户报的「刚搜过的词回发现态看不到」）
    store.set('find_search_history', ['宫保鸡丁', '红烧肉'])

    s.clearKeyword()
    // 应重读到存储里的最新两条，而非内存里的一条
    expect(s.historyList.value).toEqual(['宫保鸡丁', '红烧肉'])
  })

  it('发现态下清空关键词不触发多余重读', async () => {
    const s = useFindState()
    s.keyword.value = 'x'
    store.set('find_search_history', ['仅存储有'])
    s.clearKeyword()
    // 未进过结果态 ⇒ 不重读，内存仍为空
    expect(s.historyList.value).toEqual([])
  })

  it('清空关键词后旧结果不再残留（状态与内容一致）', async () => {
    searchMock.mockResolvedValueOnce([{ id: 1, name: '红烧肉', price: 1 }])
    const s = useFindState()
    s.keyword.value = '红烧肉'
    await s.onSearchConfirm()
    expect(s.mixedResults.value).toHaveLength(1)

    s.clearKeyword()
    // 只清 keyword 会留下「输入框已空、列表仍是旧结果」的坑
    expect(s.mixedResults.value).toEqual([])
  })
})

describe('useFindState · 记录管理', () => {
  it('removeHistory 按下标删除并落盘', () => {
    const s = useFindState()
    s.historyList.value = ['a', 'b']
    s.removeHistory(0)
    expect(s.historyList.value).toEqual(['b'])
    expect(store.get('find_search_history')).toEqual(['b'])
  })

  it('loadHistory 以存储为真源', () => {
    store.set('find_search_history', ['x', 'y'])
    const s = useFindState()
    s.loadHistory()
    expect(s.historyList.value).toEqual(['x', 'y'])
  })

  it('onRetrySearch 按当前关键词重跑', async () => {
    searchMock.mockRejectedValueOnce(new Error('boom'))
    const s = useFindState()
    s.keyword.value = '红烧肉'
    await s.onSearchConfirm()
    expect(s.searchFailed.value).toBe(true)

    searchMock.mockResolvedValueOnce([{ id: 1, name: '红烧肉', price: 1 }])
    await s.onRetrySearch()
    expect(s.searchFailed.value).toBe(false)
  })

  it('onLoadMoreResults 透传当前关键词分页', async () => {
    searchMock.mockResolvedValueOnce([
      ...Array.from({ length: 20 }, (_, i) => ({ id: i + 1, name: `菜${i + 1}`, price: 1 })),
    ])
    const s = useFindState()
    s.keyword.value = '菜'
    await s.onSearchConfirm()

    searchMock.mockResolvedValueOnce([{ id: 21, name: '菜21', price: 1 }])
    await s.onLoadMoreResults()
    expect(searchMock.mock.calls[1][0].keyword).toBe('菜')
    expect(searchMock.mock.calls[1][0].page).toBe(2)
  })
})
