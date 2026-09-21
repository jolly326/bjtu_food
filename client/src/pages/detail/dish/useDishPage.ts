/**
 * useDishPage —— 菜品详情页（pages/detail/dish/index.vue）编排逻辑
 *
 * 页面私有编排（仅本页使用，就近置于页面包，不驻留 composables/）：
 * 页面仅保留模板贴片组装与包内子件（ImageSwiper / ReviewComposer / DishInfoCard /
 * DishSummaryCard / DishReviewSection）引用。职责：
 * - 数据流：onLoad 解析 id → resetDishDetail → 并行取详情 / 公开评价 / 我的评价（判定底栏双态）
 *   + 上报浏览；
 * - 顶部大图滚动模型（dish-hero-scroll-model）：sticky 两阶段定格的全部几何量；
 * - 评价分页（触底加载，结束判据 = 已加载条数 ≥ total）、删除本人评价、写评价 / 重新评价弹层、
 *   评价三点菜单、举报（useReport，游客免认证）；
 * - 底栏「写评价 / 重新评价」双态与 ReviewComposer 预填（design D3）。
 *
 * 坐标与距离（utils/location、distance、CAMPUS_CENTER）已于 2026-09-20 全链下线，本文件不再持有定位逻辑。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useDishPage()，
 * 使 store 获取与 onLoad/onPageScroll/onReachBottom/onShareAppMessage/onMounted
 * 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, computed, onMounted } from 'vue'
import { onLoad, onShareAppMessage, onPageScroll, onReachBottom } from '@dcloudio/uni-app'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { addView } from '@/api/dish'
import { deleteReview, getMyReviews } from '@/api/review'
import type { Review, ReviewSubmittedPayload } from '@/types/review'
import { useReport } from './useReport'
import { sharedDish } from '@/utils/share-state'
import { backToHome } from '@/utils/nav'
import { getNavBarHeight } from '@/utils/navMetrics'
import { dishDetailUrl } from '@/utils/routes'
import { COLOR_MAP, MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'

/** 评价分页每页条数（详情页固定 10） */
const REVIEW_PAGE_SIZE = 10

export function useDishPage() {
  const dishStore = useDishStore()
  const userStore = useUserStore()

  const dishId = ref(0)
  const dish = computed(() => dishStore.currentDish)
  const reviewList = computed(() => dishStore.reviewList)
  const reviewTotal = computed(() => dishStore.reviewTotal)
  const currentUserId = computed(() => userStore.userInfo?.id)
  /** 评价首屏/刷新失败态（PR-03）：失败 ≠ 零评价，由评价卡渲染可重试失败块 */
  const reviewFailed = computed(() => dishStore.reviewError)
  /** 详情请求失败态（失败 ≠ 加载中 ≠ 不存在）：驱动页面失败 / 不存在文案与恢复路径 */
  const detailFailed = computed(() => dishStore.detailError)
  /** onLoad 缺少 / 非法菜品 id：同样按失败态呈现（不留纯空白页） */
  const missingDishId = ref(false)

  /**
   * 非 append（重置式）评价请求在途计数：驱动评价区**在途期空白静默**。
   * 覆盖首屏拉取与「只看有图」切换——先清空再拉取期间不得误闪「暂无带图评价 / 还没有人评价」；
   * 页面上不呈现任何骨架屏 / loading 指示（§4.8 红线）。
   * 用计数而非布尔：删除后重拉、提交后重拉可能与切换并发，计数可正确收敛。
   */
  const reviewPendingCount = ref(0)
  const reviewPending = computed(() => reviewPendingCount.value > 0)

  /** 重置式评价拉取（首屏 / 只看有图切换 / 重试 / 提交后与删除后刷新共用），置 pending 门控 */
  async function fetchReviewsReset() {
    if (!dishId.value) return
    reviewPendingCount.value += 1
    try {
      await dishStore.fetchReviews(dishId.value, { pageSize: REVIEW_PAGE_SIZE, hasImage: imageOnly.value })
    } finally {
      reviewPendingCount.value -= 1
    }
  }

  /** 当前用户对本菜的评价（判定底栏双态 + 重评预填）；游客 / 未认证恒为 null */
  const myReview = ref<Review | null>(null)

  /** 「只看有图」开关（服务端过滤：total 与分页同口径） */
  const imageOnly = ref(false)

  /** detail-modular-review-cleanup：评价卡内触底分页 */
  const reviewPage = ref(1)
  const reviewFinished = ref(false)
  const reviewLoadingMore = ref(false)
  function resetReviewPaging() {
    reviewPage.value = 1
    reviewFinished.value = false
    reviewLoadingMore.value = false
  }

  /** 触底加载下一页评价（D6：结束判据 = 已加载条数 ≥ total） */
  async function onReviewsReachBottom() {
    if (!dish.value || reviewLoadingMore.value || reviewFinished.value) return
    // 已加载条数 ≥ 服务端 total：直接判定结束，不再多发一次空请求（末页恰好满页场景）
    if (reviewList.value.length >= reviewTotal.value) {
      reviewFinished.value = true
      return
    }
    reviewLoadingMore.value = true
    try {
      // 排序唯一时间倒序，端上不传 sort（PR-02）
      const res = await dishStore.fetchReviews(dishId.value, {
        page: reviewPage.value + 1,
        pageSize: REVIEW_PAGE_SIZE,
        append: true,
        hasImage: imageOnly.value,
      })
      // null = 请求失败/被更新请求过期淘汰（store 竞态守卫）：分页不推进，保留重试机会
      if (!res) return
      reviewPage.value += 1
      // 结束判据：已加载条数 ≥ 服务端同口径 total（末页恰好满页时不再多发空请求）
      if (reviewList.value.length >= res.total) reviewFinished.value = true
    } catch { /* 底部加载失败静默，后续滚动可重试 */ } finally { reviewLoadingMore.value = false }
  }

  /**
   * 切换「只看有图」：重置分页 + 清空列表后按新口径重拉。
   * 在途期由 reviewPending 驱动评价区空白静默（不误闪空态）；空态由评价卡按新口径渲染。
   */
  function onToggleImageOnly() {
    imageOnly.value = !imageOnly.value
    resetReviewPaging()
    dishStore.clearReviews()
    void fetchReviewsReset()
  }

  /** 大图列表：优先 images，回退单图 */
  const heroImages = computed(() => {
    const d = dish.value
    if (!d) return []
    return d.images && d.images.length > 0 ? d.images : []
  })

  /* ===== dish-detail-visual-polish：覆盖导航 + 滚动渐显菜名 ===== */
  const statusBarHeight = ref(20)
  const navBarHeight = ref(56)
  const scrollTop = ref(0)
  /** 右上角原生胶囊避让：与 AppHeader 同款计算（screenW − menuBtn.left + 8px），仅微信端生效 */
  const rightPad = ref(0)
  const topPad = computed(() => `max(${statusBarHeight.value}px, env(safe-area-inset-top))`)
  /** 导航行右侧安全留白：避让微信胶囊，长菜名省略于胶囊左侧 */
  const navPadRight = computed(() =>
    rightPad.value > 0 ? `calc(env(safe-area-inset-right, 0px) + ${rightPad.value}px)` : '0px',
  )
  /** 视口尺寸（px，onMounted 取真值；缺省兜底） */
  const windowHeight = ref(800)
  const windowWidth = ref(375)

  /** AppHeader 底部留白 --spacing-sm（16rpx）折算 px：承接线口径含它，与其它二级页 AppHeader 底边对齐 */
  const spacingSmPx = ref(8)
  /** 承接线 = 状态栏 + 导航行 + AppHeader 底部留白（与其他二级页 AppHeader 底边同高的水平线） */
  const pinLine = computed(() => statusBarHeight.value + navBarHeight.value + spacingSmPx.value)
  /** 大图满高：≈ 屏高 1/4（沿用 26vh 口径，px 与滚动量同单位）；恒大于承接线，保证有定格区间 */
  const heroBase = computed(() => Math.max(Math.round(windowHeight.value * 0.26), pinLine.value + 20))
  /** 大图底边到达承接线所需滚动量 = 满高 − 承接线（sticky top 取 -pinStart，阶段 A/B 分界） */
  const pinStart = computed(() => Math.max(heroBase.value - pinLine.value, 1))
  /** 内容块最小高度（px）：即使菜品内容不足一屏，也让页面可滚动量 ≥ pinStart，
   *  保证"无论如何"都能把顶部大图滑到 header 定格位（内容较多时该下限自动失效）。 */
  const dishBodyMin = computed(() => Math.max(0, windowHeight.value + pinStart.value - heroBase.value))
  /** 承接条淡入位移：越过承接线后 48px 内淡入完成（先于标题渐显，避免浅条下出现标题） */
  const CARRY_FADE_PX = 48
  /** 承接条不透明度：大图定格后 .hero-carry 在 pinLine 高淡入实底（连续映射，无阶跃/空窗）；dish==null 由固定条承接 */
  const carryOpacity = computed(() => {
    if (dish.value == null) return 1
    const overscroll = scrollTop.value - pinStart.value
    return Math.min(1, Math.max(0, overscroll / CARRY_FADE_PX))
  })
  /** 实底态：驱动返回图标色（实底黑 / 图片态白）与 .dish-nav-back::before 深色圆底显隐 */
  const navSolid = computed(() => dish.value == null || carryOpacity.value >= 1)

  /* ===== D1e 标题渐显公式：卡片菜名滚出导航条下沿后才渐显，避免同屏两份菜名 ===== */
  /** 渐显起点：大图底边定格于承接线后，信息卡菜名滚出承接线才渐显 */
  const NAME_EXIT_SLACK = 40
  /** 沿用既有 96px 淡入区间 */
  const TITLE_FADE_SPAN = 96
  const titleFadeStart = computed(() => pinStart.value + NAME_EXIT_SLACK)
  const navOpacity = computed(() => {
    if (dish.value == null) return 1
    const p = (scrollTop.value - titleFadeStart.value) / TITLE_FADE_SPAN
    return Math.min(1, Math.max(0, p))
  })
  const dishName = computed(() => (dish.value ? dish.value.name : '菜品详情'))
  /** 页面级滚动同步（原内层 scroll-view @scroll 移除）：只驱动承接条/标题的 opacity，不再参与布局/位移。
   *  平台例外：uni 滚动回调只声明本组件真正读取的字段（MP-08，替代裸 any） */
  onPageScroll((e: { scrollTop?: number }) => {
    scrollTop.value = e?.scrollTop || 0
  })
  /** 页面滚动到底（原 scroll-view @scrolltolower）：评价触底加载下一页 */
  onReachBottom(() => {
    onReviewsReachBottom()
  })
  onMounted(() => {
    // 与 AppHeader 同款导航尺寸计算（自定义导航 + 右上角胶囊避让）
    // 平台例外：wx 全局仅存在于微信运行时，H5 分支由 w 判空兜底
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const w: any = (globalThis as any).wx
    const win = w ? (w.getWindowInfo ? w.getWindowInfo() : (w.getSystemInfoSync ? w.getSystemInfoSync() : null)) : null
    const sb = (win && win.statusBarHeight) || 20
    statusBarHeight.value = sb
    // dish-hero-scroll-model：大图满高取视口高度 26%（px），与滚动量同单位
    windowHeight.value = (win && win.windowHeight) || 800
    windowWidth.value = (win && win.windowWidth) || 375
    // --spacing-sm（16rpx）折算 px：承接线需与其它页 AppHeader 底部留白对齐
    spacingSmPx.value = (16 * windowWidth.value) / 750
    const mb = w && w.getMenuButtonBoundingClientRect ? w.getMenuButtonBoundingClientRect() : null
    if (mb && mb.height) {
      navBarHeight.value = getNavBarHeight(sb, mb)
      // 胶囊避让：screenW − 胶囊.left + 8px（px，不随屏宽缩放），与 AppHeader 一致
      const screenW = (win && win.windowWidth) || 375
      rightPad.value = Math.max(screenW - mb.left + 8, 0)
    }
  })

  /** 位置文案：食堂 · 楼层 · 档口名（窗口号与距离已下线） */
  const locationText = computed(() => {
    const d = dish.value
    if (!d) return ''
    const nodes: string[] = []
    if (d.canteen) nodes.push(d.canteen)
    if (d.floor) nodes.push(String(d.floor))
    if (d.stallName) nodes.push(d.stallName)
    return nodes.join(' · ') || '未知位置'
  })

  /** 评分分布：按星级 5→1 排序（供综合评分卡） */
  const ratingDistribution = computed(() => {
    const list = (dish.value?.ratingDistribution || []).slice()
    list.sort((a, b) => b.star - a.star)
    return list
  })

  onLoad((query) => {
    const id = Number(query?.id)
    if (!id) {
      // 缺 ID：同「不存在」按失败态呈现（明确文案 + 返回），不留纯空白页
      missingDishId.value = true
      uni.showToast({ title: '缺少菜品ID', icon: 'none' })
      return
    }
    missingDishId.value = false
    dishId.value = id
    dishStore.resetDishDetail()
    myReview.value = null
    imageOnly.value = false
    void loadDishData()
  })

  /** 进入页面并行取数：详情 + 公开评价 + 我的评价（判定底栏态），并上报浏览 */
  async function loadDishData() {
    if (!dishId.value) return
    resetReviewPaging()
    addView(dishId.value)
    const tasks: Promise<unknown>[] = [
      dishStore.fetchDetail(dishId.value),
      fetchReviewsReset(),
    ]
    // 已认证用户才判定「我是否已评价」：未认证（游客）跳过，底栏按「未评价」呈现
    if (userStore.isVerified()) tasks.push(loadMyReview())
    await Promise.all(tasks)
    syncSharedDish()
  }

  /** 拉取「我的评价（按菜过滤）」：判定底栏双态并取回评价 ID 供重评预填 */
  async function loadMyReview() {
    if (!dishId.value) return
    try {
      const res = await getMyReviews({ dishId: dishId.value, page: 1, pageSize: 1 })
      myReview.value = res.list[0] ?? null
    } catch {
      // 判定失败静默降级为「未评价」，不阻塞详情展示
      myReview.value = null
    }
  }

  /** 详情请求失败后重试（与进入页面同路径，仅重拉详情） */
  function onRetryDetail() {
    if (!dishId.value) return
    dishStore.fetchDetail(dishId.value)
  }

  /** 写回分享态（供 onShareAppMessage 读取菜名 + 现价） */
  function syncSharedDish() {
    const d = dish.value
    if (d) {
      sharedDish.value = { id: d.id, name: d.name, price: d.price, stallName: d.stallName }
    } else {
      sharedDish.value = null
    }
  }

  onShareAppMessage(() => ({
    title: dish.value ? `${dish.value.name} ¥${dish.value.price}` : '菜品详情',
    path: dishDetailUrl(dishId.value),
  }))

  /** 删除本人评价：成功后重拉列表 + 刷新综合评分 */
  function onDeleteReview(rv: Review) {
    if (!userStore.requireAuth(() => onDeleteReview(rv))) return
    if (userStore.userInfo?.id && rv.userId !== userStore.userInfo.id) return
    uni.showModal({
      title: '删除评价',
      content: '确定删除这条评价吗？删除后不可恢复。',
      confirmText: '删除',
      confirmColor: MODAL_CONFIRM_DANGER_COLOR,
      success: async (res) => {
        if (!res.confirm) return
        try {
          await deleteReview(rv.id)
          uni.showToast({ title: '评价已删除', icon: 'none' })
          // 删除的若是本人评价：判定态回退为「未评价」
          if (myReview.value?.id === rv.id) myReview.value = null
          resetReviewPaging()
          await fetchReviewsReset()
          dishStore.fetchDetail(dishId.value)
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      },
    })
  }

  /* ===== 写评价 / 重新评价（底栏左钮 → ReviewComposer 底部抽屉） ===== */
  const composerOpen = ref(false)
  /** 底栏主按钮文案：已评价 = 重新评价，未评价 = 写评价（游客恒为「写评价」） */
  const reviewButtonText = computed(() => (myReview.value ? '重新评价' : '写评价'))
  /** 重评预填（评分 / 文字 / 配图）；未评价为 null */
  const composerPrefill = computed(() => {
    const rv = myReview.value
    if (!rv) return null
    return { rating: rv.rating, content: rv.content, images: rv.images ?? [] }
  })
  /** 重评目标评价 ID；未评价为 null（走首次发表 POST） */
  const composerReviewId = computed(() => myReview.value?.id ?? null)

  function onOpenReviewComposer() {
    if (!dish.value) return
    // 与删除/举报同款：未认证先弹认证（AuthSheet），认证完成后回调重进本函数
    if (!userStore.requireAuth(() => onOpenReviewComposer())) return
    composerOpen.value = true
  }

  /**
   * 提交成功：
   * - 重评：**本地写回** myReview（新值），底栏就地保持「重新评价」，不重新判定（design D3）；
   * - 首次发表：回读「我的评价」取回 id，使后续入口切为「重新评价」；
   * 两种情况均重置分页并按当前「只看有图」口径重拉评价 + 刷新综合评分。
   */
  function onReviewSubmitted(payload: ReviewSubmittedPayload) {
    if (payload.mode === 'update' && myReview.value) {
      myReview.value = {
        ...myReview.value,
        rating: payload.rating,
        content: payload.content,
        images: payload.images,
      }
    } else {
      void loadMyReview()
    }
    resetReviewPaging()
    void fetchReviewsReset()
    dishStore.fetchDetail(dishId.value)
  }

  /** 评价失败态点击重试（PR-03）：重置分页后从第 1 页重拉，与进入页面同路径 */
  function onRetryReviews() {
    if (!dishId.value) return
    resetReviewPaging()
    void fetchReviewsReset()
  }

  /* ===== 评价三点菜单（ReviewItem @more → 页面级通用 ActionSheet） ===== */
  const reviewMoreOpen = ref(false)
  const reviewMoreTarget = ref<Review | null>(null)
  const reviewMoreIsOwn = computed(() => {
    const rv = reviewMoreTarget.value
    return rv != null && userStore.userInfo?.id != null && rv.userId === userStore.userInfo.id
  })

  function onReviewMore(rv: Review) {
    reviewMoreTarget.value = rv
    reviewMoreOpen.value = true
  }

  /** 动作项：本人删除 / 他人举报（危险操作警示色） */
  const reviewMoreItems = computed(() => {
    if (!reviewMoreTarget.value) return []
    return reviewMoreIsOwn.value
      // iconColor 走 IconSvg 的 :color → SVG data-uri 无法解析 var()，必须是真源实色（D1）；
      // textColor 落 CSS color 属性，var() 正常生效，保持语义 token 形态。
      ? [{ key: 'delete', label: '删除评价', icon: 'delete', iconColor: COLOR_MAP.error, textColor: 'var(--color-error)' }]
      : [{ key: 'report', label: '举报评价', icon: 'report', iconColor: COLOR_MAP.error, textColor: 'var(--color-error)' }]
  })

  function onReviewMoreSelect(key: string) {
    const rv = reviewMoreTarget.value
    if (!rv) return
    if (key === 'delete') onDeleteReview(rv)
    else if (key === 'report') onReviewReport(rv)
  }

  /* ===== 评价举报（收敛到 useReport hook） ===== */
  const { reportOpen, reportSubmitting, openReport, submitReport } =
    useReport({ type: 'review', title: '举报评价', placeholder: '请描述举报原因…' })

  function onReviewReport(rv: Review) {
    openReport(rv.id)
  }

  /** 供页面模板/模板回调使用的全部编排绑定（名称与抽取前 <script setup> 顶层保持一致） */
  return {
    dish,
    dishId,
    dishName,
    dishBodyMin,
    heroImages,
    heroBase,
    pinLine,
    pinStart,
    carryOpacity,
    navOpacity,
    navSolid,
    topPad,
    navPadRight,
    navBarHeight,
    locationText,
    ratingDistribution,
    reviewList,
    reviewTotal,
    reviewFailed,
    reviewPending,
    detailFailed,
    missingDishId,
    imageOnly,
    currentUserId,
    myReview,
    reviewButtonText,
    composerPrefill,
    composerReviewId,
    composerOpen,
    reportOpen,
    reportSubmitting,
    reviewMoreOpen,
    reviewMoreItems,
    backToHome,
    onRetryDetail,
    onToggleImageOnly,
    onDeleteReview,
    onReviewReport,
    onReviewMore,
    onReviewMoreSelect,
    onOpenReviewComposer,
    onReviewSubmitted,
    onRetryReviews,
    submitReport,
  }
}
