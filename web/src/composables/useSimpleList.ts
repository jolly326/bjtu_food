import { shallowRef } from 'vue'
import { createListState } from './listState'
import { createSeqGuard } from '@/utils/seq-guard'

/**
 * 非分页列表组合式（A1 食堂 / A2 档口 / A4 维度与取值 / A5 Banner / A6 视图与分类值 / A7 举报原因）。
 *
 * 这些列表**量级极小、接口不分页**（`data` 直接是数组），故与 `usePagedList` 分开；
 * 四态口径与错误处置共用 {@link createListState}，与分页列表页**逐字一致**。
 */
export function useSimpleList<T>(fetcher: () => Promise<T[]>) {
  const items = shallowRef<T[]>([])
  const state = createListState(items)
  /** 并发守卫：取号后按新旧裁决，过期响应一律丢弃（写操作后刷新撞上在途加载时不覆盖新结果） */
  const seq = createSeqGuard()

  /** 加载 / 重新加载（写操作成功后调用即「刷新」） */
  async function load(): Promise<void> {
    const my = seq.begin()
    state.begin()
    try {
      const next = [...(await fetcher())]
      if (!seq.isCurrent(my)) return
      items.value = next
    } catch (e) {
      if (!seq.isCurrent(my)) return
      state.fail(e)
    } finally {
      state.settle()
    }
  }

  const { loading, error, sessionInvalid, firstLoading, isEmpty, hasData } = state
  return { items, loading, firstLoading, isEmpty, hasData, error, sessionInvalid, load }
}
