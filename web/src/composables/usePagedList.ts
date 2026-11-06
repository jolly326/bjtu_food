/**
 * 列表页分页状态机（见 web/src/components/PaginationBar.vue）。
 *
 * <p>解耦点：视图只声明「取一页数据」的 fetcher，翻页 / 总数 / 总页数 / 加载态 /
 * 下一页可用性 / 末页删空回退 / 筛选回第 1 页 / 加载失败提示统一在此单点维护 ——
 * 各列表视图共享同一份状态机。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Page } from '@/api/types'

/** 页大小（与服务端默认一致；「下一页」可用性据此计算，单点常量） */
const PAGE_SIZE = 20

export function usePagedList<T>(fetchPage: (page: number) => Promise<Page<T>>) {
  const rows = ref<T[]>([])
  const total = ref(0)
  const page = ref(1)
  const loading = ref(false)
  /** 最近一次加载失败的原因（空 = 无错误；供页面展示持久错误块 + 重试） */
  const error = ref('')
  /** 还有下一页（`page * PAGE_SIZE < total`） */
  const hasNext = computed(() => page.value * PAGE_SIZE < total.value)
  /** 总页数（分页器单点计算，页面不各自 `Math.ceil`） */
  const totalPages = computed(() => Math.ceil(total.value / PAGE_SIZE))
  /** 请求序号：只接受最后一次发出的响应，旧响应迟到即丢弃（连点翻页 / 筛选抖动） */
  let seq = 0

  async function load(): Promise<void> {
    const current = ++seq
    loading.value = true
    error.value = ''
    try {
      const res = await fetchPage(page.value)
      if (current !== seq) return
      // 末页删空自动回退：本页查空且不在第 1 页 ⇒ 回退一页重查（每跳至少 -1，终止于第 1 页）
      if (!res.records.length && page.value > 1) {
        page.value -= 1
        return await load()
      }
      rows.value = res.records
      total.value = res.total
    } catch (e) {
      if (current !== seq) return
      error.value = (e as Error).message
      ElMessage.error(error.value)
    } finally {
      if (current === seq) loading.value = false
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

  return { rows, total, page, loading, error, hasNext, totalPages, load, go, reload }
}
