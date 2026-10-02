import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { DishListItem } from '@/types/dish'
import { RESULT_PAGE_SIZE } from '@/constants/paging'

/**
 * `useSearchResults` 回归测试 —— 本模块由 `pages/find/index.vue` 拆出（本次重构产物），
 * 承载搜索页最易被改坏的三件事：竞态守卫 / 三态互斥 / 分页结束判据。
 *
 * <p>用 deferred（可控 promise）而非计时器构造竞态：确定性，不依赖时序。
 */
const searchMock = vi.fn()
vi.mock('@/stores/dish', () => ({
  useDishStore: () => ({ search: searchMock }),
}))

// 在 mock 之后动态 import：确保被测模块内部 useDishStore() 取到 mock
const { useSearchResults } = await import('./useSearchResults')

/** 造一行菜品（仅提供被测逻辑读到的字段） */
function dish(id: number, name = `菜${id}`): DishListItem {
  return {
    id,
    name,
    coverImage: 'c.jpg',
    canteen: '一食堂',
    stallName: '窗口1',
    price: 10,
    rating: 4.5,
  } as unknown as DishListItem
}

/** 造 n 行，offset 用于生成跨页不重复的 id */
function dishes(n: number, offset = 0): DishListItem[] {
  return Array.from({ length: n }, (_, i) => dish(i + 1 + offset))
}

/** 可手动兑现的 promise，用于构造「慢请求」 */
function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  searchMock.mockReset()
  // 被测代码用 console.error 记录失败；测试中静音以免污染报告
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
describe('useSearchResults · 三态', () => {
  it('成功且无结果 → 无结果态（done=true, failed=false），而非失败态', async () => {
    searchMock.mockResolvedValue([])
    const r = useSearchResults()
    await r.search('不存在的菜')

    expect(r.searchDone.value).toBe(true)
    // 关键：空结果**不得**置 failed，否则页面渲染「重试块」而非空态
    expect(r.searchFailed.value).toBe(false)
    expect(r.mixedResults.value).toEqual([])
  })

  it('请求抛错 → 失败态（done=true, failed=true），不伪装成空结果', async () => {
    searchMock.mockRejectedValue(new Error('boom'))
    const r = useSearchResults()
    await r.search('炸了')

    expect(r.searchDone.value).toBe(true)
    expect(r.searchFailed.value).toBe(true)
    // 失败时清空残留结果，避免旧结果与失败态并存
    expect(r.mixedResults.value).toEqual([])
  })

  it('失败后 retry 成功 → 失败态被清除', async () => {
    searchMock.mockRejectedValueOnce(new Error('boom'))
    const r = useSearchResults()
    await r.search('甲')
    expect(r.searchFailed.value).toBe(true)

    searchMock.mockResolvedValueOnce([dish(1)])
    await r.retry('甲')

    expect(r.searchFailed.value).toBe(false)
    expect(r.mixedResults.value).toHaveLength(1)
  })

  it('空关键词不发请求（守卫）', async () => {
    const r = useSearchResults()
    await r.search('')
    expect(searchMock).not.toHaveBeenCalled()
    expect(r.inFilter.value).toBe(false)
  })

  it('行映射：无 name 的菜品被丢弃，副信息走 joinLocation 口径', async () => {
    searchMock.mockResolvedValue([
      { ...dish(1), name: '' },
      { ...dish(2), canteen: '一食堂', stallName: '窗口1' },
    ])
    const r = useSearchResults()
    await r.search('x')

    expect(r.mixedResults.value).toHaveLength(1)
    expect(r.mixedResults.value[0].sub).toBe('一食堂 · 窗口1')
    expect(r.mixedResults.value[0].type).toBe('dish')
  })
})

describe('useSearchResults · 竞态守卫', () => {
  it('慢的旧请求不得覆盖快的后发请求', async () => {
    const slow = deferred<DishListItem[]>()
    const fast = deferred<DishListItem[]>()
    searchMock.mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise)

    const r = useSearchResults()
    const p1 = r.search('旧词')
    const p2 = r.search('新词')

    // 后发的「新词」先返回
    fast.resolve([dish(99, '新词结果')])
    await p2
    // 旧词此时才返回 —— 必须被丢弃
    slow.resolve([dish(1, '旧词结果')])
    await p1

    expect(r.mixedResults.value).toHaveLength(1)
    expect(r.mixedResults.value[0].name).toBe('新词结果')
  })

  it('过期请求的失败不得把结果态打成失败态', async () => {
    const slow = deferred<DishListItem[]>()
    const fast = deferred<DishListItem[]>()
    searchMock.mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise)

    const r = useSearchResults()
    const p1 = r.search('旧词')
    const p2 = r.search('新词')
    fast.resolve([dish(1)])
    await p2

    slow.reject(new Error('旧请求失败'))
    await p1

    // 关键：旧请求的失败不得污染当前结果
    expect(r.searchFailed.value).toBe(false)
    expect(r.mixedResults.value).toHaveLength(1)
  })

  it('exit 使在途请求失效：其返回后不写回结果', async () => {
    const slow = deferred<DishListItem[]>()
    searchMock.mockReturnValueOnce(slow.promise)

    const r = useSearchResults()
    const p = r.search('甲')
    r.exit()
    slow.resolve([dish(1)])
    await p

    expect(r.mixedResults.value).toEqual([])
    expect(r.inFilter.value).toBe(false)
  })

  it('连续搜索不设防重入锁：后发请求照常发出', async () => {
    searchMock.mockResolvedValue([])
    const r = useSearchResults()
    await r.search('a')
    await r.search('b')
    // 若加防重入锁，第二次会被静默丢弃、界面停留在旧结果
    expect(searchMock).toHaveBeenCalledTimes(2)
  })
})

describe('useSearchResults · 分页', () => {
  it('满页不封底（后端无 total，满页不能判定还有下一页）', async () => {
    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE))
    const r = useSearchResults()
    await r.search('x')
    expect(r.resultsFinished.value).toBe(false)
  })

  it('不足一页即封底', async () => {
    searchMock.mockResolvedValueOnce(dishes(3))
    const r = useSearchResults()
    await r.search('x')
    expect(r.resultsFinished.value).toBe(true)
  })

  it('触底追加下一页并累加页码', async () => {
    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE))
    const r = useSearchResults()
    await r.search('x')
    expect(r.mixedResults.value).toHaveLength(RESULT_PAGE_SIZE)

    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE, RESULT_PAGE_SIZE))
    await r.loadMore('x')

    expect(r.mixedResults.value).toHaveLength(RESULT_PAGE_SIZE * 2)
    expect(searchMock.mock.calls[1][0].page).toBe(2)
  })

  it('已到底时触底不再发请求', async () => {
    searchMock.mockResolvedValueOnce(dishes(2))
    const r = useSearchResults()
    await r.search('x')
    expect(r.resultsFinished.value).toBe(true)

    await r.loadMore('x')
    expect(searchMock).toHaveBeenCalledTimes(1)
  })

  it('分页失败静默：不置失败态、不打断滚动、页码回退可重试同页', async () => {
    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE))
    const r = useSearchResults()
    await r.search('x')

    searchMock.mockRejectedValueOnce(new Error('分页失败'))
    await r.loadMore('x')

    // 关键：分页失败**不得**置 searchFailed（会渲染整页重试块，中断滚动）
    expect(r.searchFailed.value).toBe(false)
    expect(r.resultsFinished.value).toBe(false)
    expect(r.loadingMore.value).toBe(false)

    // 页码未推进：重试仍请求第 2 页
    searchMock.mockResolvedValueOnce(dishes(2, RESULT_PAGE_SIZE))
    await r.loadMore('x')
    expect(searchMock.mock.calls[2][0].page).toBe(2)
    expect(r.mixedResults.value).toHaveLength(RESULT_PAGE_SIZE + 2)
    expect(r.resultsFinished.value).toBe(true)
  })

  it('分页在途时重复触底被拦截（并发去重）', async () => {
    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE))
    const r = useSearchResults()
    await r.search('x')

    const slow = deferred<DishListItem[]>()
    searchMock.mockReturnValueOnce(slow.promise)
    const p1 = r.loadMore('x')
    await r.loadMore('x')

    expect(searchMock).toHaveBeenCalledTimes(2)
    slow.resolve(dishes(2, RESULT_PAGE_SIZE))
    await p1
  })

  it('首屏在途时触底被跳过（页码尚未落定，避免错位）', async () => {
    const slow = deferred<DishListItem[]>()
    searchMock.mockReturnValueOnce(slow.promise)
    const r = useSearchResults()
    const p = r.search('x')

    await r.loadMore('x')
    expect(searchMock).toHaveBeenCalledTimes(1)

    slow.resolve(dishes(RESULT_PAGE_SIZE))
    await p
  })

  it('空白关键词触底被拦截', async () => {
    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE))
    const r = useSearchResults()
    await r.search('x')
    await r.loadMore('   ')
    expect(searchMock).toHaveBeenCalledTimes(1)
  })

  it('分页去重：重复 id 不追加', async () => {
    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE))
    const r = useSearchResults()
    await r.search('x')

    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE))
    await r.loadMore('x')
    expect(r.mixedResults.value).toHaveLength(RESULT_PAGE_SIZE)
  })

  it('分页返回 0 条即封底（唯一可靠的封口信号）', async () => {
    searchMock.mockResolvedValueOnce(dishes(RESULT_PAGE_SIZE))
    const r = useSearchResults()
    await r.search('x')

    searchMock.mockResolvedValueOnce([])
    await r.loadMore('x')
    expect(r.resultsFinished.value).toBe(true)
    expect(r.mixedResults.value).toHaveLength(RESULT_PAGE_SIZE)
  })
})

describe('useSearchResults · exit', () => {
  it('清空全部结果态并回退页码', async () => {
    searchMock.mockResolvedValueOnce(dishes(5))
    const r = useSearchResults()
    await r.search('x')

    r.exit()
    expect(r.inFilter.value).toBe(false)
    expect(r.mixedResults.value).toEqual([])
    expect(r.searchDone.value).toBe(false)
    expect(r.searchFailed.value).toBe(false)
    expect(r.resultsFinished.value).toBe(false)
  })
})
