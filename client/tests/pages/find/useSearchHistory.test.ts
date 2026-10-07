import { describe, it, expect, vi, beforeEach } from 'vitest'

/**
 * `useSearchHistory` 回归测试（搜索记录，纯本地）。
 *
 * <p>覆盖三条易被改坏的约束：
 * ① 置顶去重（点已存在的历史词不应产生重复项、不应重新置顶）；
 * ② 上限 4 条；
 * ③ **读失败保留内存副本**（不清空）—— 该模块会在每次 `onShow` 被调用，
 *    瞬时失败若清空就会抹掉用户刚看到的记录。
 *
 * <p>`uni` 为小程序运行时 API，node 环境无此全局，用最小 mock 替代
 * （只实现被测代码用到的 getStorageSync / setStorageSync / showModal）。
 */
const store = new Map<string, unknown>()
const showModal = vi.fn()

beforeEach(() => {
  store.clear()
  showModal.mockReset()
  showModal.mockImplementation((opts: { success?: (r: { confirm: boolean }) => void }) => {
    // 默认「确认」，各用例按需覆盖
    opts.success?.({ confirm: true })
  })
  ;(globalThis as Record<string, unknown>).uni = {
    getStorageSync: (k: string) => store.get(k),
    setStorageSync: (k: string, v: unknown) => {
      store.set(k, v)
    },
    showModal,
  }
})

const { useSearchHistory } = await import('@/pages/find/useSearchHistory')

describe('useSearchHistory · 置顶与去重', () => {
  it('新词置顶', () => {
    const h = useSearchHistory()
    h.push('红烧肉')
    h.push('宫保鸡丁')
    expect(h.historyList.value).toEqual(['宫保鸡丁', '红烧肉'])
  })

  it('点已存在的历史词不产生重复项', () => {
    const h = useSearchHistory()
    h.push('红烧肉')
    h.push('宫保鸡丁')
    h.push('红烧肉')
    expect(h.historyList.value).toEqual(['红烧肉', '宫保鸡丁'])
    expect(h.historyList.value).toHaveLength(2)
  })

  it('空白词不入库（守卫）', () => {
    const h = useSearchHistory()
    h.push('   ')
    h.push('')
    expect(h.historyList.value).toEqual([])
  })

  it('入库存 trimmed 值', () => {
    const h = useSearchHistory()
    h.push('  红烧肉  ')
    expect(h.historyList.value).toEqual(['红烧肉'])
  })

  it('超出上限 4 条时裁掉尾部', () => {
    const h = useSearchHistory()
    h.push('a')
    h.push('b')
    h.push('c')
    h.push('d')
    expect(h.historyList.value).toEqual(['d', 'c', 'b', 'a'])
    h.push('e')
    expect(h.historyList.value).toEqual(['e', 'd', 'c', 'b'])
    expect(h.historyList.value).toHaveLength(4)
  })
})

describe('useSearchHistory · 存储读写', () => {
  it('push 落盘（onShow 重读时以存储为真源）', () => {
    const h = useSearchHistory()
    h.push('红烧肉')
    expect(store.get('find_search_history')).toEqual(['红烧肉'])
  })

  it('load 以存储为唯一真源，覆盖内存副本', () => {
    const h = useSearchHistory()
    h.historyList.value = ['内存旧值']
    store.set('find_search_history', ['存储新值'])
    h.load()
    expect(h.historyList.value).toEqual(['存储新值'])
  })

  it('load 对超限数据做裁剪（防止旧版本超限数据全量灌入）', () => {
    store.set('find_search_history', ['a', 'b', 'c', 'd', 'e', 'f'])
    const h = useSearchHistory()
    h.load()
    expect(h.historyList.value).toEqual(['a', 'b', 'c', 'd'])
  })

  it('**读失败保留内存副本，不清空**（该模块每次 onShow 都会被调用）', () => {
    const h = useSearchHistory()
    h.push('红烧肉')
    // 模拟存储读取抛错
    ;(globalThis as Record<string, unknown>).uni = {
      getStorageSync: () => {
        throw new Error('storage down')
      },
      setStorageSync: vi.fn(),
      showModal,
    }
    h.load()
    expect(h.historyList.value).toEqual(['红烧肉'])
  })

  it('存储写入失败降级为「仅内存」，不抛错', () => {
    ;(globalThis as Record<string, unknown>).uni = {
      getStorageSync: (k: string) => store.get(k),
      setStorageSync: () => {
        throw new Error('quota exceeded')
      },
      showModal,
    }
    const h = useSearchHistory()
    expect(() => h.push('红烧肉')).not.toThrow()
    expect(h.historyList.value).toEqual(['红烧肉'])
  })

  it('存储中的非数组脏数据不破坏内存副本', () => {
    const h = useSearchHistory()
    h.push('红烧肉')
    store.set('find_search_history', '不是数组')
    h.load()
    expect(h.historyList.value).toEqual(['红烧肉'])
  })
})

describe('useSearchHistory · 删除', () => {
  it('remove 按下标删除并落盘', () => {
    const h = useSearchHistory()
    h.push('a')
    h.push('b')
    h.remove(0)
    expect(h.historyList.value).toEqual(['a'])
    expect(store.get('find_search_history')).toEqual(['a'])
  })

  it('clear 需二次确认：取消则不清空', () => {
    showModal.mockImplementation((o: { success?: (r: { confirm: boolean }) => void }) => o.success?.({ confirm: false }))
    const h = useSearchHistory()
    h.push('a')
    h.clear()
    expect(h.historyList.value).toEqual(['a'])
  })

  it('clear 确认后清空并落盘', () => {
    const h = useSearchHistory()
    h.push('a')
    h.clear()
    expect(h.historyList.value).toEqual([])
    expect(store.get('find_search_history')).toEqual([])
  })

  it('clear 走破坏性二次确认（防误触）', () => {
    const h = useSearchHistory()
    h.clear()
    expect(showModal).toHaveBeenCalledTimes(1)
    const opts = showModal.mock.calls[0][0]
    expect(opts.confirmText).toBe('清空')
  })
})
