/**
 * 搜索页编排层：两态的**切换规则**与跨态动作。
 *
 * <p><b>为何存在这一层</b>：发现态与结果态各自持有自己的数据（历史 / 猜你喜欢 vs 检索结果），
 * 但它们之间有**跨态动作**——「输入框提交」「点搜索记录 / 猜你喜欢词条」都会
 * *从发现态切到结果态并发起检索*。这部分不属于任何一个子模块，由本层收口。
 *
 * <p><b>不做什么</b>：本层**不持有数据**（历史与结果分别在自己的 composable 内），
 * 也不直接渲染；仅负责「谁在什么时机调用谁」。这样两个子模块可以各自独立演进。
 *
 * <p><b>两种发起检索的写入口径差异</b>（产品语义，不可合并）：
 * · **「猜你喜欢」词条 → 写入搜索记录**（`record = true`）：同样是一次用户主动发起的搜索，
 *   与「打字后提交」心智等价，理应可回溯（此前不写入造成「搜过却没有记录」的困惑）；
 * · **「搜索记录」词条 → 不写入**（`record = false`）：该词本就在记录内，
 *   重搜无需再置顶；上限仅 4 条，重复写入只会打乱既有顺序。
 */
import { ref } from 'vue'
import { useSearchHistory } from './useSearchHistory'
import { useSearchResults } from './useSearchResults'

export function useFindState() {
  const { historyList, load: loadHistory, push: pushHistory, remove: removeHistory, clear: clearHistory } =
    useSearchHistory()
  const results = useSearchResults()

  /** 搜索词（两态共享：输入框在壳层，点词条也要写它） */
  const keyword = ref('')

  /** 确认 / 回车搜索（SearchBar input 模式的 @search：回车 / 点「搜索」按钮） */
  function submit() {
    const kw = keyword.value.trim()
    if (!kw) return
    pushHistory(kw)
    void results.search(kw)
  }

  /**
   * 点词条（搜索记录 / 猜你喜欢）：以该词发起搜索。
   *
   * @param record 是否写入搜索记录（猜你喜欢=true / 搜索记录=false，见类注释）
   */
  function tapKeyword(kw: string, record = false) {
    keyword.value = kw
    if (record) pushHistory(kw)
    void results.search(kw)
  }

  /**
   * 清空关键词 = 「重新开始」：清词 **并**退出结果态回发现态。
   *
   * <p>只清 keyword 会留下两个坑：① 输入框已空、列表仍是旧结果（状态与内容不一致）；
   * ② 此后点「搜索」无词可搜 —— 旧实现静默 return，用户读作「点了没反应」。
   */
  function clearKeyword() {
    keyword.value = ''
    if (results.inFilter.value) {
      results.exit()
      // 回发现态时**重读**搜索记录（以存储为唯一真源）。
      // 此前只在 onMounted 读一次 —— 本页被页面栈缓存（返回再进不重新挂载）时，
      // 内存副本一旦滞后于存储，搜索记录就不会更新（用户报的「刚搜过的词回发现态看不到」）。
      loadHistory()
    }
  }

  /** 失败块重试：按当前关键词重跑（竞态守卫在 results.search 内） */
  function retrySearch() {
    return results.retry(keyword.value)
  }

  /** 触底加载下一页（包装：传入当前关键词；底层按 in-flight / 到底守卫） */
  function loadMoreResults() {
    return results.loadMore(keyword.value)
  }

  return {
    keyword,
    historyList,
    loadHistory,
    removeHistory,
    clearHistory,
    tapKeyword,
    onSearchConfirm: submit,
    clearKeyword,
    onRetrySearch: retrySearch,
    onLoadMoreResults: loadMoreResults,
    ...results,
  }
}
