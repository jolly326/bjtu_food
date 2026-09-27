/**
 * 分页列表（触底加载更多）—— 全站统一的取数语义
 *
 * 背景（UI 统一 Loop Round 17 · 代码质量轮）：`notifications` 与 `my-reviews` 两页各写了一套
 * **几乎逐字相同**的分页样板：`loading` 重入守卫、第 1 页重拉、`finished` 到底判定、
 * 触底 `page += 1` → 去重 `concat` → 失败 `page -= 1` 回退、首屏失败置 `loadFailed`（失败 ≠ 空数据）。
 * 抽到本 composable 后语义只有一份，页面只注入自己的**差异**（游客跳过 / 成功副作用 / 失败日志标签）。
 *
 * 与页面的契约：
 * · 页面模板继续使用返回的 `list` / `loading` / `loadFailed` / `finished`（命名与原实现一致 ⇒ 模板零改动）；
 * · 首屏重拉统一走 `load()`（`onShow` 闸门与失败重试块 @tap 共用同一路径）；
 * · 触底统一走 `loadMore()`（`scroll-view` 的 `@scrolltolower` 或页面 `onReachBottom`）。
 *
 * ⚠️ 约定：把页面滚动容器改为 `scroll-view` / 固定高度布局时，触底事件必须由滚动区承载
 * （页面级 `onReachBottom` 不再触发 —— Round 17 踩坑记录；历史见 git 归档）。
 */
import { ref, type Ref } from 'vue'

export interface UsePagedListOptions<T extends { id: number }> {
  /** 拉取第 `page` 页（页码从 1 开始），返回本页行数组 */
  fetchPage: (page: number, pageSize: number) => Promise<T[]>
  /** 每页条数（默认 20，与既有两页一致） */
  pageSize?: number
  /**
   * 首屏前置守卫：返回 false 时**不发请求**，直接落空列表并标记到底
   * （我的评价：游客调需登录端点必 401 ⇒ 跳过并走游客空态引导）。
   */
  canLoad?: () => boolean
  /** 触底前置守卫：返回 false 时跳过本次分页（通知页：游客无个人数据） */
  canLoadMore?: () => boolean
  /** 首屏成功回调（列表已写入）：刷新未读数、复位「删除导致空列表」标记等页面级副作用 */
  onLoadSuccess?: (rows: T[]) => void
  /** 首屏结束回调（成功 / 失败均调用；守卫短路路径不算）：如置「已加载完成」驱动空态判定 */
  onLoadSettled?: () => void
  /** 首屏失败的日志标签（保持各页原有文案，便于定位） */
  loadFailLabel: string
}

export interface UsePagedListReturn<T> {
  list: Ref<T[]>
  /** 请求在途：首屏与分页**共用**（天然互斥，同时兼作重入守卫） */
  loading: Ref<boolean>
  /** 首屏失败（失败 ≠ 空数据；页面据此渲染可重试失败块而非空态） */
  loadFailed: Ref<boolean>
  /** 是否已到底（本页不足 `pageSize` 即到底） */
  finished: Ref<boolean>
  /** 首屏重拉（第 1 页）：`onShow` 闸门与失败重试块 @tap 共用 */
  load: () => Promise<void>
  /** 触底加载下一页：去重追加；失败回退页码，再次触底即重试同一页（静默，不打断滚动） */
  loadMore: () => Promise<void>
}

export function usePagedList<T extends { id: number }>(
  options: UsePagedListOptions<T>,
): UsePagedListReturn<T> {
  const { fetchPage, pageSize = 20, canLoad, canLoadMore, onLoadSuccess, onLoadSettled, loadFailLabel } = options

  const list = ref<T[]>([]) as Ref<T[]>
  const loading = ref(false)
  const loadFailed = ref(false)
  const finished = ref(false)
  let page = 1

  async function load(): Promise<void> {
    if (loading.value) return
    if (canLoad && !canLoad()) {
      // 守卫短路：不发请求、落空列表并标记到底（与原先各页的「游客」分支行为一致）
      list.value = []
      loadFailed.value = false
      finished.value = true
      return
    }
    loading.value = true
    try {
      const rows = await fetchPage(1, pageSize)
      loadFailed.value = false
      list.value = rows
      page = 1
      finished.value = rows.length < pageSize
      onLoadSuccess?.(rows)
    } catch (err) {
      console.error(loadFailLabel, err)
      loadFailed.value = true
    } finally {
      loading.value = false
      onLoadSettled?.()
    }
  }

  async function loadMore(): Promise<void> {
    if (finished.value || loading.value) return
    if (canLoadMore && !canLoadMore()) return
    loading.value = true
    try {
      page += 1
      const rows = await fetchPage(page, pageSize)
      // 去重（极端情况下分页跳号），避免重复行
      const existIds = new Set(list.value.map((it) => it.id))
      list.value = list.value.concat(rows.filter((it) => !existIds.has(it.id)))
      if (rows.length < pageSize) finished.value = true
    } catch {
      // 失败回退页码（保持静默：不打断滚动；再次触底会重试同一页）
      page -= 1
    } finally {
      loading.value = false
    }
  }

  return { list, loading, loadFailed, finished, load, loadMore }
}
