import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { DishListItem, DishDetail, DishQuery, GuessLike, DishView } from '@/types/dish'
import type { Review } from '@/types/review'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import { isResourceNotFound } from '@/api/http'
import { HOME_PAGE_SIZE, REVIEW_PAGE_SIZE } from '@/constants/paging'
import { mergePagedRows } from '@/composables/usePagedList'

/**
 * 首页列表单页条数（`fetchHomeDishes` / `loadMoreHomeDishes` 共用，防口径漂移）。
 * 分页口径已收敛至 `constants/paging`（单一真源）。此处保留导出，
 * 供 `pages/home/HomeContent.vue` 等既有消费方继续引用，避免牵连改名。
 */
export { HOME_PAGE_SIZE }
/**
 * 首页列表最大保留页数：10 页 × 10 条 = 100 条封顶。
 * 深翻后 `homeList` 无上限增长会让 `HomeContent` 的列分配每次全量重算（低端机掉帧）；
 * 到底不再静默截断，改由 `homePageLimited` 驱动页面给出「已展示前 N 个结果」提示。
 */
export const HOME_MAX_PAGES = 10

/** loading key：首页列表首屏 / 切视图（供 `HomeContent` 判定「静默加载中」） */
export const LOADING_KEY_HOME = 'home'
/** loading key：首页列表触底加载更多（模块私有；对外由 `homeLoadingMore` 派生） */
const LOADING_KEY_HOME_MORE = 'homeMore'
/**
 * loading key：**切视图**（模块私有）。
 * 与 `LOADING_KEY_HOME` 分开登记，是为了让 `HomeContent` 的「静默加载中」判定（只订阅 `LOADING_KEY_HOME`）
 * **不被切视图触发**——切视图时**保留旧列表在屏**，否则列表被清空 + 内容塌成 0 会让
 * `scroll-view` 把滚动位置钳回顶部（用户可见 bug：切标签弹回首页顶部）。
 */
const LOADING_KEY_HOME_SWAP = 'homeSwap'
/**
 * 评价列表在途登记 key（模块私有）。
 */
const REVIEWS_LOADING_KEY = 'fetchReviews'

export const useDishStore = defineStore('dish', () => {
  const currentDish = ref<DishDetail | null>(null)
  const reviewList = ref<Review[]>([])
  /**
   * 详情首屏/刷新是否失败（失败 ≠ 加载中 ≠ 不存在）：
   * 供详情页区分「静默加载中（空白）」与「请求失败（明确文案 + 重试/返回）」两种态。
   */
  const detailError = ref(false)
  /**
   * 菜品**不存在**（后端 `4001`，§7.40 R8）—— 与「请求失败」**区别对待**：
   * 不存在（含已下架，下架对外等价于不存在）**不可重试**，页面应只给「返回」路径；
   * 网络 / 服务端故障才给「重新加载」。二者**互斥**（同一时刻至多一个为 true）。
   */
  const detailNotFound = ref(false)
  /**
   * 在途请求登记：单一 loading 被多个并发请求共享会互相提前解除（S-6）。
   * 必须是**响应式 Set**，否则 `computed(() => inFlight.size > 0)` 取不到依赖。
   */
  const inFlight = ref<Set<string>>(new Set())

  /** 按 key 派生在途态：消费方只订阅自己关心的那一个请求 */
  function isLoading(key: string): boolean {
    return inFlight.value.has(key)
  }

  /** 包裹异步请求：进入时登记 key，结束（成功/失败）时移除，保证并发互不干扰 */
  async function withLoading<T>(key: string, fn: () => Promise<T>): Promise<T> {
    inFlight.value.add(key)
    try {
      return await fn()
    } finally {
      inFlight.value.delete(key)
    }
  }

  // ==================== 首页列表流（食堂 / 价格筛选不提供） ====================

  /** 筛选视图字典（`GET /dishes/views`）：文案 / 顺序 / 子集全由后端下发，端上零写死 */
  const viewList = ref<DishView[]>([])
  /** 当前选中视图键（`null` = 字典尚未加载，请求不传 `view` ⇒ 服务端落默认视图）；端上唯一筛选维度 */
  const filterView = ref<string | null>(null)

  /** 首页列表（默认视图 = 推荐流·会话种子伪随机序（逛）；大类视图 = 该类热度序（找），产品拍板确认保持） */
  const homeList = ref<DishListItem[]>([])
  const homePage = ref(1)
  /** 触底加载更多是否在途（派生自 loading key，兼作 loadMore 并发守卫） */
  const homeLoadingMore = computed(() => isLoading(LOADING_KEY_HOME_MORE))
  const homeFinished = ref(false)
  /** 是否已触达保留页数上限：true 时不再 concat 新页，由页面给触底提示 */
  const homePageLimited = ref(false)
  /** 列表最近一次请求是否失败（失败 ≠ 空数据）；过期响应不修改本状态 */
  const homeError = ref(false)

  /** 列表请求序号：快速切换视图时丢弃过期响应，避免旧请求晚到覆盖新列表 */
  let homeFetchSeq = 0

  /**
   * 生成一个随机种子串（时间戳 base36 + 随机串 base36，约 15 字符）。
   * 服务端只把它当**稳定哈希的输入**（`CRC32(CONCAT(seed,'-',id))`），不做格式校验。
   */
  function genSeed(): string {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
  }

  /**
   * **会话级**种子基准：模块求值时生成一次 ⇒ 小程序**冷启动（重进）才重掷**。
   *
   * <p>为何是「会话级」而非「每次 reset 重掷」（原方案 C 口径，推翻）：
   * 端上**没有任何「主动换一批」入口**（全仓无 `onPullDownRefresh`），内容却在切视图 / 重试后
   * 自己变化 ⇒ 用户只能读成「界面不稳定」，而不是「拿到了新鲜内容」。
   * 随机性应归于**用户主动动作**（重进小程序），不归于「每次读数据」——
   * 否则同一次浏览内顺序漂移，翻页还会重复 / 漏项。
   *
   * <p>为何用基准**派生**而不是各区块共用同一个 seed：推荐流与猜你喜欢若共用同一 seed，
   * 两处取的是「同一全序」的前 N 个 ⇒ 首屏与发现态出现**同一批菜**，观感像 bug。
   */
  const sessionSeedBase = genSeed()

  /**
   * 首页推荐流种子（`GET /dishes` 的 `seed`，方案 C）——会话内**恒定**：
   * - 随每次请求一起下发（服务端仅对「推荐类」视图消费它，其余视图忽略 ⇒ 端上无需判断视图语义）；
   * - 同一次会话内翻页沿用同一值 ⇒ 服务端全序恒定，触底加载不跨页重复 / 漏项；
   * - 切视图 / 失败重试**不重掷** ⇒ 回到「为你推荐」拿到的仍是同一顺序（内容不自变）。
   * 模块级 const（非 ref）：纯请求内部态，无需响应式，不对外暴露。
   */
  const homeSeed = `${sessionSeedBase}-home`

  /**
   * 猜你喜欢种子（`GET /dishes/for-you` 的 `seed`）——与推荐流**同源不同值**，
   * 使发现态词条与首屏推荐流不出现同一批菜（推导见上方 `sessionSeedBase`）。
   */
  const guessLikeSeed = `${sessionSeedBase}-guess`

  /**
   * 视图字典是否**已成功**加载。
   * 用于 `fetchDishViews` 去重：字典是静态字典，成功拉到后无需再拉；
   * **失败不置位** ⇒ 页面 onShow 的「兜底重试」仍会重试（与既有注释语义一致）。
   * 本 store 内部状态，不对外暴露。
   */
  const viewLoaded = ref(false)
  /**
   * 视图字典请求是否**在途**（同上）：`onLoad` 与 `onShow` 会在首次进入时先后触发同一次拉取，
   * 仅靠「已成功」标记挡不住并发重复 ⇒ 补在途标记，二者共同去重。
   */
  const viewLoading = ref(false)

  /**
   * 拉取筛选视图字典（**标签栏 100% 服务端直出，端上零文案**）。
   * 顺带校正选中项：选中视图不在字典（该类当前无在售菜被隐藏）或尚未选中 → 落**字典首项**
   * （服务端声明的默认视图），避免请求一个不存在的视图。
   */
  async function fetchDishViews() {
    // 去重：已成功拉过、或已有同一请求在途，都不再发；
    // 失败路径不置位 ⇒ 页面 onShow 的兜底重试仍会重试（与原注释语义一致）。
    if (viewLoaded.value || viewLoading.value) return
    viewLoading.value = true
    try {
      const list = await dishApi.getDishViews()
      viewList.value = list
      viewLoaded.value = true
      // 字典首项 = 服务端声明的默认视图（如「为你推荐」）；端上不硬编码其 key
      const [first] = list
      const fallback = first ? first.key : null
      if (!filterView.value || !list.some((v) => v.key === filterView.value)) {
        filterView.value = fallback
      }
    } catch (e) {
      // 失败**不写任何端上兜底项**：标签文案是**服务端资产**
      // （含「为你推荐」、将来「折扣」等视图都在服务端装配），端上拼一个同名字符串
      // ⇒ 改文案 / 加视图又要发版，违背「标签栏全量服务端直出」。
      // 故此处只记录：保留上一次结果（若有），标签栏由 `HomeMealTabs` 判空**整体不渲染**；
      // 列表仍按「不传 view」的默认流加载，功能不受影响。
      console.error('加载筛选视图失败', e)
    } finally {
      viewLoading.value = false
    }
  }

  /**
   * 切换筛选视图：写回选中键并重置分页刷新列表。
   * **不重置页面滚动位置**（UI 文档 §11.3 边界行为）：因此走 `keepList = true` —— 新数据到手前
   * 旧列表留在屏上（stale-while-revalidate），避免内容塌陷把滚动位置钳到顶部。
   */
  async function setHomeView(viewKey: string) {
    filterView.value = viewKey
    await fetchHomeDishes(true, true)
  }

  /** 首页搜索请求（首刷与触底共用同一份参数装配：view / seed / page / pageSize） */
  function searchHome() {
    return dishApi.searchDishesPage({
      view: filterView.value ?? undefined,
      seed: homeSeed,
      page: homePage.value,
      pageSize: HOME_PAGE_SIZE,
    })
  }

  /**
   * reset 的分页状态复位：`keepList`（切视图）旧列表留屏只重置分页；
   * 其余场景（首屏 / 重试）清列表。失败态同步清（成功后本就为 false，本次失败会再置 true）。
   * **不重掷种子**——刷新边界 = 重进小程序。
   */
  function resetHomePagination(keepList: boolean) {
    if (!keepList) homeList.value = []
    homePage.value = 1
    homeFinished.value = false
    homePageLimited.value = false
    homeError.value = false
  }

  /**
   * 首页列表拉取（`reset=true` 表示切视图 / 首屏 / 重试：清列表、回到第 1 页）。
   * 不传任何食堂 / 价格条件，也不传 `sortBy` / `sortOrder`（筛选与排序由所选视图决定）；
   * `seed` 随每次请求一起下发——服务端仅对「推荐类」视图消费它（方案 C），其余视图忽略。
   */
  async function fetchHomeDishes(reset = false, keepList = false) {
    return withLoading(keepList ? LOADING_KEY_HOME_SWAP : LOADING_KEY_HOME, async () => {
      const seq = ++homeFetchSeq
      if (reset) resetHomePagination(keepList)
      try {
        const res = await searchHome()
        // 过期响应（期间又切换了视图）直接丢弃，不覆盖新列表
        if (seq !== homeFetchSeq) return
        // append 分支收敛到 mergePagedRows（单一真源）：与 fetchReviews 同口径，补 id 去重防分页跳号重复行
        homeList.value = reset ? res.list : mergePagedRows(homeList.value, res.list, HOME_PAGE_SIZE).rows
        homeError.value = false
        // 结束判据基于「本页返回条数 < pageSize」
        if (res.list.length < HOME_PAGE_SIZE) homeFinished.value = true
      } catch (e) {
        if (seq !== homeFetchSeq) return
        console.error('加载菜品列表失败', e)
        homeError.value = true
      }
    })
  }

  /**
   * 首页列表触底加载更多：页数达 `HOME_MAX_PAGES` 后不再 concat（置 `homePageLimited`）。
   * 方案 C：与首刷**沿用同一 `homeSeed`**（不在此重掷）⇒ 服务端全序恒定，翻页不重不漏。
   */
  async function loadMoreHomeDishes(): Promise<boolean> {
    if (homeLoadingMore.value || homeFinished.value) return false
    // 竞态修复：首刷 / 切视图的**重置式请求**在途时禁止翻页 ——
    // 二者与 loadMore 共用 `homeFetchSeq`，loadMore 推进序号会让在途的重置响应被判过期丢弃，
    // 而第 2 页却按**新筛选**拼到**旧列表**上 ⇒ 列表内容错乱（大类与数据不匹配）。
    if (isLoading(LOADING_KEY_HOME) || isLoading(LOADING_KEY_HOME_SWAP)) return false
    if (homePage.value >= HOME_MAX_PAGES) {
      homeFinished.value = true
      homePageLimited.value = true
      return false
    }
    const seq = ++homeFetchSeq
    homePage.value += 1
    return withLoading(LOADING_KEY_HOME_MORE, async () => {
      try {
        const res = await searchHome()
        if (seq !== homeFetchSeq) {
          homePage.value -= 1
          return false
        }
        homeList.value = mergePagedRows(homeList.value, res.list, HOME_PAGE_SIZE).rows
        if (res.list.length < HOME_PAGE_SIZE) {
          homeFinished.value = true
        } else if (homePage.value >= HOME_MAX_PAGES) {
          homeFinished.value = true
          homePageLimited.value = true
        }
        return res.list.length > 0
      } catch (e) {
        console.error('加载更多菜品失败', e)
        homePage.value -= 1
        return false
      }
    })
  }

  // ==================== 搜索（find 页） ====================

  async function search(query: DishQuery): Promise<DishListItem[]> {
    try {
      return await withLoading('search', async () => await dishApi.searchDishes(query))
    } catch (e) {
      // MP-012：失败不再静默吞成空数组（会被误读为「没有结果」）——向上抛错，
      // 由唯一消费方（find 搜索流）的 catch 区分「失败」与「无结果」
      console.error('搜索失败', e)
      throw e
    }
  }

  // ==================== 详情与评价 ====================

  async function fetchDetail(id: number) {
    return withLoading('fetchDetail', async () => {
      currentDish.value = await dishApi.getDishDetail(id)
      detailError.value = false
      detailNotFound.value = false
    }).catch((e) => {
      console.error('加载菜品详情失败', e)
      currentDish.value = null
      // 4001（资源不存在，R8）→ 不存在态（不可重试）；其余（网络 / 5xx）→ 失败态（可重试）
      detailNotFound.value = isResourceNotFound(e)
      detailError.value = !detailNotFound.value
    })
  }

  /** 进入新菜品前清空旧详情与评价态，避免闪现上一道菜（store 全局状态残留） */
  function resetDishDetail() {
    // 使所有在途评价请求失效：旧菜品的触底 append 晚到时不再写入新菜品列表（竞态守卫）
    reviewFetchSeq++
    currentDish.value = null
    detailError.value = false
    detailNotFound.value = false
    reviewList.value = []
    reviewError.value = false
  }

  /** 清空评价列表：供重置式拉取前先清后拉（避免旧结果短暂残留） */
  function clearReviews() {
    reviewList.value = []
  }

  /**
   * 本地移除一条评价（删除成功后对齐列表，避免为一行变化重拉整页）。
   * 「评价已不存在」（后端 4001）时同样走它 —— 该码重试无意义，按已删除收尾。
   */
  function removeReview(id: number) {
    reviewList.value = reviewList.value.filter((x) => x.id !== id)
  }

  /** 评价首屏/刷新是否失败（失败 ≠ 零评价）；过期响应不修改本状态 */
  const reviewError = ref(false)

  /** 评价请求序号：翻页 / 进新菜品时丢弃过期响应，防触底 append 与 reset 交错 */
  let reviewFetchSeq = 0

  /**
   * 评价区分页（RESTful 子资源 `GET /dishes/{id}/reviews`）。
   * 排序唯一为时间倒序。
   *
   * **契约（唯一）**：过期 / 失败一律返回 `null` —— 调用方据此跳过分页推进（避免永久跳过该页）。
   * 分页壳只有 `records`，**到底判据 = 本页返回条数 < `pageSize`**（由调用方判定）。
   */
  async function fetchReviews(
    dishId: number,
    options?: { page?: number; pageSize?: number; append?: boolean },
  ): Promise<{ list: Review[] } | null> {
    const seq = ++reviewFetchSeq
    const page = options?.page ?? 1
    const pageSize = options?.pageSize ?? REVIEW_PAGE_SIZE
    try {
      const res = await withLoading(REVIEWS_LOADING_KEY, async () =>
        await reviewApi.getDishReviews(dishId, { page, pageSize }))
      // 过期响应（期间又有新请求发起 / resetDishDetail 已切菜品）：丢弃，不覆盖最新列表
      if (seq !== reviewFetchSeq) return null
      if (options?.append) {
        // 去重追加（单一真源 mergePagedRows）：与 usePagedList 同口径，防分页跳号重复行
        reviewList.value = mergePagedRows(reviewList.value, res.list, pageSize).rows
      } else {
        reviewList.value = res.list
      }
      reviewError.value = false
      return res
    } catch (e) {
      console.error('加载评价失败', e)
      if (seq !== reviewFetchSeq) return null
      if (!options?.append) {
        reviewList.value = []
        reviewError.value = true
      }
      return null
    }
  }

  /**
   * 猜你喜欢（会话级稳定伪随机在售菜品名；find 页「猜你喜欢」区块消费）。
   * 传会话级 `guessLikeSeed` ⇒ 同一次会话内多次进入发现态拿到同一批词条（内容不自变）；
   * 重进小程序 ⇒ seed 重掷 ⇒ 整体重洗。
   */
  const guessLikeList = ref<GuessLike[]>([])
  async function fetchGuessLike() {
    try {
      guessLikeList.value = await dishApi.getGuessLike(guessLikeSeed)
    } catch (e) {
      console.error('加载猜你喜欢失败', e)
      guessLikeList.value = []
    }
  }

  return {
    // 在途态
    isLoading,
    // 首页列表流（唯一筛选维度 = 筛选视图）
    viewList, filterView,
    homeList, homeLoadingMore, homePageLimited, homeError,
    fetchDishViews, setHomeView, fetchHomeDishes, loadMoreHomeDishes,
    // 搜索 / 详情 / 评价 / 热搜
    search,
    currentDish, detailError, detailNotFound, fetchDetail, resetDishDetail,
    reviewList, reviewError, fetchReviews, clearReviews, removeReview,
    guessLikeList, fetchGuessLike,
  }
})
