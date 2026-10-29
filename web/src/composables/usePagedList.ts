/**
 * 列表页分页状态机（口径见 docs/ui/web/公共组件与形态基线.md §2.3「分页」）。
 *
 * <p>解耦点：视图只声明「取一页数据」的 fetcher，翻页 / 总数 / 加载态 / 下一页可用性 /
 * 末页删空回退 / 筛选回第 1 页 / 加载失败提示统一在此单点维护 —— 各列表视图共享同一份状态机。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Page } from '@/api/types'

/** 页大小（与服务端默认一致；「下一页」可用性据此计算，单点常量） */
export const PAGE_SIZE = 20

export function usePagedList<T>(fetchPage: (page: number) => Promise<Page<T>>) {
  const rows = ref<T[]>([])
  const total = ref(0)
  const page = ref(1)
  const loading = ref(false)
  /** 最近一次加载失败的原因（空 = 无错误；供页面展示持久错误块 + 重试） */
  const error = ref('')
  /** 还有下一页（`page * PAGE_SIZE < total`） */
  const hasNext = computed(() => page.value * PAGE_SIZE < total.value)

  async function load(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      const res = await fetchPage(page.value)
      // 末页删空自动回退：本页查空且不在第 1 页 ⇒ 回退一页重查（每跳至少 -1，终止于第 1 页）
      if (!res.records.length && page.value > 1) {
        page.value -= 1
        return await load()
      }
      rows.value = res.records
      total.value = res.total
    } catch (e) {
      error.value = (e as Error).message
      ElMessage.error(error.value)
    } finally {
      loading.value = false
    }
  }

  /** 翻到指定页（按钮 disabled 态由模板按 `page` / `hasNext` 控制） */
  async function go(next: number): Promise<void> {
    page.value = Math.max(1, next)
    await load()
  }

  /** 条件 / 筛选变化：回第 1 页重查 */
  function reload(): Promise<void> {
    page.value = 1
    return load()
  }

  return { rows, total, page, loading, error, hasNext, load, go, reload }
}
