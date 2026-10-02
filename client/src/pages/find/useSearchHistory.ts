/**
 * 搜索记录（纯本地，不上服务端）。
 *
 * <p><b>为何独立成 composable</b>：原为 `pages/find/index.vue` 内的 45 行内联逻辑
 * （storage 读写 + 上限裁剪 + 三种增删 + 二次确认），而它有 **3 个调用点**
 * （`onMounted` / `onShow` / `exitFilter`）——页面被栈缓存时 `onMounted` 不再执行，
 * 须靠 `onShow` 重读，以存储为唯一真源（此前只读一次导致「刚搜的词回发现态看不到」）。
 *
 * <p><b>关键约束</b>：读失败时**保留内存副本**，不清空 —— 本模块会在多次
 * `onShow` 中被调用，瞬时失败不应清掉用户已看到的记录。
 */
import { ref } from 'vue'
import { MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'

const HISTORY_KEY = 'find_search_history'
/** 搜索记录上限 4 条（缓存与展示一致、无展开/收起） */
const HISTORY_MAX = 4

export function useSearchHistory() {
  const historyList = ref<string[]>([])

  /** 从存储重读（存储为唯一真源；失败保留内存副本） */
  function load() {
    try {
      const raw = uni.getStorageSync(HISTORY_KEY)
      if (Array.isArray(raw)) historyList.value = raw.slice(0, HISTORY_MAX)
    } catch {
      /* 读取异常：保留内存副本，不清空 */
    }
  }

  function save() {
    try {
      uni.setStorageSync(HISTORY_KEY, historyList.value)
    } catch {
      /* ignore：存储不可用时降级为「仅内存」 */
    }
  }

  /** 置顶一条并去重裁剪 */
  function push(kw: string) {
    const k = kw.trim()
    if (!k) return
    historyList.value = [k, ...historyList.value.filter((x) => x !== k)].slice(0, HISTORY_MAX)
    save()
  }

  function remove(index: number) {
    historyList.value.splice(index, 1)
    save()
  }

  /** 清空：破坏性操作，走二次确认防误触（单条删除保留即时，逐条确认会打断） */
  function clear() {
    uni.showModal({
      title: '清空搜索历史',
      content: '确定要清空全部搜索历史吗？此操作不可恢复。',
      confirmText: '清空',
      confirmColor: MODAL_CONFIRM_DANGER_COLOR,
      success: (res) => {
        if (!res.confirm) return
        historyList.value = []
        save()
      },
    })
  }

  return { historyList, load, push, remove, clear }
}
