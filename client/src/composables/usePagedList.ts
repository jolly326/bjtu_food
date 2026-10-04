/**
 * 分页列表（触底加载更多）—— 全站统一的取数语义。
 *
 * 与页面的契约：模板用返回的 `list` / `loading` / `loadFailed` / `finished`；首屏重拉走 `load()`；
 * 触底走 `loadMore()`（`scroll-view` 的 `@scrolltolower`）。
 *
 * ⚠️ 页面滚动容器若改为 `scroll-view` / 固定高度布局，触底必须由滚动区承载 —— 页面级
 * `onReachBottom` 不再触发。
 */
import { computed, onMounted, ref, watch, type Ref } from 'vue'
import { DEFAULT_PAGE_SIZE } from '@/constants/paging'

interface UsePagedListOptions<T extends { id: number }> {
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
  /** 渲染封顶页数（默认不封顶）：无虚拟化列表按此上限停止追加，避免节点无限增长 */
  maxPages?: number
}

interface UsePagedListReturn<T> {
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

/** 结束判据（单一真源）：后端 `PageResult` 无 total ⇒ 本页条数 < `pageSize` 即到底 */
export function isLastPage(rows: { length: number }, pageSize: number): boolean {
  return rows.length < pageSize
}

/**
 * 分页增量合并（单一真源）：把新一页 `incoming` 去重追加到 `current`，并判定是否到底。
 *
 * 以 `id` 为唯一键去重（极端分页跳号防护）；本页 0 长度或不足 `pageSize` 即封底
 * —— 后端仅下发明细、无 total，「满页」不能判定还有下一页，必须靠短页 / 0 长度封口。
 *
 * 调用方保留自己的「竞态守卫 / 重入锁 / 失败回退页码」逻辑；本函数只负责「合并 + 封底」。
 */
export function mergePagedRows<T extends { id?: number }>(
  current: T[],
  incoming: T[],
  pageSize: number,
): { rows: T[]; finished: boolean } {
  // 0 长度：无新增，封口到底；不触碰 current（避免空态闪现）
  if (incoming.length === 0) return { rows: current, finished: true }
  const existIds = new Set(current.map((it) => it.id))
  const rows = current.concat(incoming.filter((it) => !existIds.has(it.id)))
  return { rows, finished: isLastPage(incoming, pageSize) }
}

export function usePagedList<T extends { id: number }>(
  options: UsePagedListOptions<T>,
): UsePagedListReturn<T> {
  const {
    fetchPage,
    pageSize = DEFAULT_PAGE_SIZE,
    canLoad,
    canLoadMore,
    onLoadSuccess,
    onLoadSettled,
    maxPages = Number.POSITIVE_INFINITY,
  } = options

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
      finished.value = isLastPage(rows, pageSize)
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
    // 页数封顶：达上限即封口到底（无虚拟化列表不无限增长）
    if (page >= maxPages) {
      finished.value = true
      return
    }
    loading.value = true
    try {
      page += 1
      const rows = await fetchPage(page, pageSize)
      // 0 长度封口时 page 已 +1，但 finished 守卫会拦截后续请求，不会跳页
      const merged = mergePagedRows(list.value, rows, pageSize)
      list.value = merged.rows
      finished.value = merged.finished
    } catch {
      // 失败回退页码（静默：不打断滚动；再次触底会重试同一页）
      page -= 1
    } finally {
      loading.value = false
    }
  }

  return { list, loading, loadFailed, finished, load, loadMore }
}

/**
 * 请求序号守卫（纯逻辑，无生命周期）：并发 / 交错请求只允许**最后一次发起**的结果生效。
 *
 * 用途：分页与「重置式重拉」交错时丢弃过期响应 —— 否则 append 会推进序号，使在途的 reset 响应
 * 被判过期丢弃，列表只剩第 2 页（内容错乱）。
 *
 * 用法：`const seq = guard.begin()` 发起前取号 → 响应到达后 `if (!guard.isCurrent(seq)) return`。
 */
export function createSeqGuard() {
  let current = 0
  return {
    /** 发起请求前取号（自增） */
    begin: (): number => ++current,
    /** 响应到达后判定：`false` = 已被更新的请求淘汰，应丢弃本次结果 */
    isCurrent: (seq: number): boolean => seq === current,
    /** 读取当前号但**不取号**：供「分页借当前号判定自己是否被淘汰」用（取号会误杀在途的重置请求） */
    peek: (): number => current,
    /** 作废所有在途请求（只自增不取号）：如重置详情时让旧请求的响应一律失效 */
    invalidate: (): void => {
      current += 1
    },
  }
}

/**
 * 虚拟列表（scroll-view 专用）：只渲染**可视窗口**内的条目，把长列表 DOM 节点数从 O(n) 降到 O(窗口)。
 *
 * ⚠️ **不要再拆成独立的 useVirtualList.ts**：mp-weixin 的模块注册只覆盖主包入口可达的模块，仅被分包引用的
 * 独立模块不进主包模块图 ⇒ 分包 require 时报 module 'composables/xxx.js' is not defined（MP-019 同类坑）。
 * 本文件已被主包 stores/dish.ts 引用、可稳定从分包加载。
 *
 * 用法：scroll-view 绑 @scroll="onScroll" 并加类 v-scroll；条目容器内首尾各放一个占位 view
 * （topPad / bottomPad），中间 v-for="x in visible"；每个条目根节点加 class="v-item"。
 * 条目数 ≤ threshold 时**不虚拟化**（全渲染）⇒ 短列表零回归面。
 */

interface UseVirtualListOptions<T> {
  /** 全量数据源（分页列表的 `list`） */
  items: Ref<T[]>
  /** 未实测条目的估算高度（px）：仅作兜底，实测后逐项收敛 */
  estimateHeight: number
  /** 可视窗口上下各多渲染的条数（防快速滚动露白） */
  overscan?: number
  /** 条目数 ≤ 该值时不虚拟化（全渲染，零回归面） */
  threshold?: number
  /** 列表相对滚动容器的固定偏移（px）；列表为滚动内容首块时为 0 */
  offset?: number
  /** 列表容器类名（如 `.review-card`）：提供时动态实测偏移（优先于 `offset`） */
  offsetSelector?: string
  /** 实测选择的条目根类名（默认 `v-item`） */
  itemClass?: string
  /** 滚动容器的类名（默认 `v-scroll`，须与模板一致） */
  scrollClass?: string
  /** 外部滚动量来源（列表无法自行绑定 `@scroll` 时由父级下发）；提供时内建 `onScroll` 不再被使用 */
  scrollTopSource?: Ref<number>
  /** 选择器作用域（在**自定义组件内**使用时传 `getCurrentInstance()?.proxy`，否则查询命中不到组件内节点） */
  scope?: unknown
}

export function useVirtualList<T>(opts: UseVirtualListOptions<T>) {
  const { items, estimateHeight, offsetSelector } = opts
  const overscan = opts.overscan ?? 4
  const threshold = opts.threshold ?? 60
  const itemClass = opts.itemClass ?? 'v-item'
  const scrollClass = opts.scrollClass ?? 'v-scroll'

  /** 内建滚动量（未提供外部来源时使用） */
  const internalScrollTop = ref(0)
  /** 当前滚动量：优先外部来源（列表嵌在父级 `scroll-view` 内时由父级下发） */
  const scrollTop = computed(() => (opts.scrollTopSource ? opts.scrollTopSource.value : internalScrollTop.value))
  /** 可视高（px）：挂载后实测回填，未量到前用保守缺省 */
  const viewportH = ref(800)
  /** 实测高度表（缺省 0 ⇒ 取估算值） */
  const heights = ref<number[]>([])
  /** 动态实测的列表偏移（px）：仅 `offsetSelector` 模式下使用 */
  const measuredOffset = ref(opts.offset ?? 0)

  /** 列表偏移：动态实测优先，否则用固定 `offset` */
  const offsetRef = computed(() => (offsetSelector ? measuredOffset.value : opts.offset ?? 0))

  /** 是否启用虚拟化：短列表全渲染 */
  const enabled = computed(() => items.value.length > threshold)

  /** 累计偏移：`offsets[i]` = 前 i 项高度和（未实测取估算） */
  const offsets = computed(() => {
    const n = items.value.length
    const arr = new Array<number>(n + 1)
    arr[0] = 0
    for (let i = 0; i < n; i++) arr[i + 1] = arr[i] + (heights.value[i] || estimateHeight)
    return arr
  })
  const totalHeight = computed(() => offsets.value[items.value.length] || 0)

  /** 窗口起点：二分定位可视区首项，再向左多取 `overscan` 项 */
  const startIndex = computed(() => {
    if (!enabled.value) return 0
    const rel = scrollTop.value - offsetRef.value
    if (rel <= 0) return 0
    const offs = offsets.value
    let lo = 0
    let hi = items.value.length - 1
    let ans = 0
    while (lo <= hi) {
      const mid = (lo + hi) >> 1
      if (offs[mid + 1] <= rel) {
        lo = mid + 1
      } else {
        ans = mid
        hi = mid - 1
      }
    }
    return Math.max(0, ans - overscan)
  })

  /** 窗口终点：从起点向下累积到超出可视底，再向右多取 `overscan` 项 */
  const endIndex = computed(() => {
    if (!enabled.value) return items.value.length
    const rel = Math.max(0, scrollTop.value - offsetRef.value)
    const bottom = rel + viewportH.value
    const offs = offsets.value
    let i = startIndex.value
    while (i < items.value.length && offs[i] < bottom) i += 1
    return Math.min(items.value.length, i + overscan)
  })

  const visible = computed(() => items.value.slice(startIndex.value, endIndex.value))
  const topPad = computed(() => (enabled.value ? offsets.value[startIndex.value] || 0 : 0))
  const bottomPad = computed(() =>
    enabled.value ? Math.max(0, totalHeight.value - (offsets.value[endIndex.value] || 0)) : 0,
  )

  /** `scroll-view` 的 `@scroll`：驱动窗口重算 */
  function onScroll(e: { detail: { scrollTop: number } }) {
    internalScrollTop.value = e.detail.scrollTop || 0
  }

  /** 实测：量取可视高 + 列表偏移 + 当前窗口内各条目真实高度（量不到的项保持估算） */
  function measure() {
    if (!enabled.value) return
    const query = opts.scope
      ? uni.createSelectorQuery().in(opts.scope as never)
      : uni.createSelectorQuery()
    query.select(`.${scrollClass}`).boundingClientRect()
    query.select(offsetSelector || `.__vlist-none__`).boundingClientRect()
    query.selectAll(`.${itemClass}`).boundingClientRect()
    query.exec((res) => {
      const arr = res as Array<unknown>
      const scrollRect = arr[0] as { height?: number; top?: number } | null
      const offsetRect = arr[1] as { top?: number } | null
      const sh = scrollRect?.height
      if (sh && sh > 0) viewportH.value = sh
      // 偏移 = （列表顶 - 滚动容器顶）+ 已滚动距离（随滚动位置换算，恒定正确）
      if (offsetSelector && scrollRect?.top != null && offsetRect?.top != null) {
        measuredOffset.value = Math.max(0, offsetRect.top - scrollRect.top + scrollTop.value)
      }
      const rects = (Array.isArray(arr[2]) ? arr[2] : []) as Array<{ height?: number } | null>
      const base = startIndex.value
      const next = heights.value.slice()
      let changed = false
      rects.forEach((r, i) => {
        const h = r && typeof r.height === 'number' ? r.height : 0
        if (h > 0 && Math.abs((next[base + i] || 0) - h) > 0.5) {
          next[base + i] = h
          changed = true
        }
      })
      if (changed) heights.value = next
    })
  }

  // 窗口变化后（DOM 已更新）量一次；挂载后补一次（首屏可视高与首屏条目）
  watch([startIndex, endIndex, () => items.value.length], measure, { flush: 'post' })
  onMounted(measure)

  return { onScroll, visible, topPad, bottomPad, enabled, startIndex, endIndex }
}



