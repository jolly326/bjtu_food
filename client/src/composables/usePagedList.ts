/**
 * 分页列表（触底加载更多）—— 全站统一的取数语义
 *
 * 背景：`notifications` 与 `my-reviews` 两页各写了一套
 * **几乎逐字相同**的分页样板：`loading` 重入守卫、第 1 页重拉、`finished` 到底判定、
 * 触底 `page += 1` → 去重 `concat` → 失败 `page -= 1` 回退、首屏失败置 `loadFailed`（失败 ≠ 空数据）。
 * 抽到本 composable 后语义只有一份，页面只注入自己的**差异**（游客跳过 / 成功副作用）。
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
import { DEFAULT_PAGE_SIZE } from '@/constants/paging'

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

/**
 * 分页增量合并（单一真源）：把新一页 `incoming` 去重追加到 `current`，并判定是否到底。
 *
 * <p>去重：以 `id` 为唯一键，丢弃已在列表中的行（极端分页跳号防护，与 `usePagedList` 同源）。
 * <p>封底：本页 `incoming.length === 0` 或 `< pageSize` 即到底（`finished = true`）——
 * 后端 `PageResult` 仅下发明细、无 total，故「满页」不能判定还有下一页，必须靠 0 长度封口。
 *
 * <p>调用方保留自己的「竞态守卫（seq）/ 重入锁 / 失败回退页码」逻辑；本函数只负责
 * 「合并 + 封底」这一份纯逻辑，供 find / review / 首页复用，避免各自手抄一份。
 */
export function mergePagedRows<T extends { id?: number }>(
  current: T[],
  incoming: T[],
  pageSize: number,
): { rows: T[]; finished: boolean } {
  if (incoming.length === 0) {
    // 0 长度：无新增，封口到底；不触碰 current（避免空态闪现）
    return { rows: current, finished: true }
  }
  const existIds = new Set(current.map((it) => it.id))
  const rows = current.concat(incoming.filter((it) => !existIds.has(it.id)))
  return { rows, finished: incoming.length < pageSize }
}

export function usePagedList<T extends { id: number }>(
  options: UsePagedListOptions<T>,
): UsePagedListReturn<T> {
  const { fetchPage, pageSize = DEFAULT_PAGE_SIZE, canLoad, canLoadMore, onLoadSuccess, onLoadSettled } = options

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
    } catch {
      // 失败仅置态（页面渲染可重试失败块），不再打日志
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
      // 合并 + 封底（单一真源 mergePagedRows）：去重追加、0 长度封口、满页未封底。
      // 0 长度即「无新增」⇒ 封口到底且 page 已 +1 不再触发后续请求（finished 守卫拦截）。
      const merged = mergePagedRows(list.value, rows, pageSize)
      list.value = merged.rows
      finished.value = merged.finished
    } catch {
      // 失败回退页码（保持静默：不打断滚动；再次触底会重试同一页）
      page -= 1
    } finally {
      loading.value = false
    }
  }

  return { list, loading, loadFailed, finished, load, loadMore }
}
