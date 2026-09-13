/**
 * useDishPage —— 菜品详情页（pages/detail/dish/index.vue）编排逻辑
 *
 * 页面私有编排（仅本页使用，就近置于页面包，不驻留 composables/）：
 * 抽取自 detail/dish/index.vue 的 <script setup>（dish-detail-visual-polish / detail-modular-review-cleanup / N07 等之后），
 * 页面仅保留模板贴片组装与包内子件（ImageSwiper / ReviewComposer / DishInfoCard / DishSummaryCard / DishReviewSection）引用。
 * 职责：
 * - 数据流：onLoad 解析 id → resetDishDetail → 定位补齐 + fetchDetail/fetchReviews（含 addView 埋点），
 *   onShow 依据 reviewsDirty 重拉；分享路径使用 current dish；
 * - 顶部大图滚动模型（dish-hero-scroll-model）：sticky 两阶段定格的全部几何量
 *   （statusBar/navBar/rightPad、heroBase/pinLine/pinStart/dishBodyMin、carryOpacity/navOpacity）；
 * - 评价分页（触底加载）、删除本人菜品/评价、写评价弹层（ReviewComposer）与提交后刷新、评价三点菜单、
 *   举报（useReport，游客免认证）；
 * - 距你距离（本地 haversine + 会话级定位补齐）。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useDishPage()，
 * 使 store 获取与 onLoad/onShow/onUnload/onPageScroll/onReachBottom/onShareAppMessage/onMounted
 * 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, computed, onMounted } from 'vue'
import { onLoad, onShow, onUnload, onShareAppMessage, onPageScroll, onReachBottom } from '@dcloudio/uni-app'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { useLocationStore } from '@/stores/location'
import { haversineMeters, getUserLocation } from '@/utils/location'
import { addView, deleteDish } from '@/api/dish'
import { deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
import { useReport } from '@/composables/useReport'
import { sharedDish } from '@/utils/share-state'
import { backToHome } from '@/utils/nav'
import { getNavBarHeight } from '@/utils/navMetrics'
import { dishDetailUrl } from '@/utils/routes'
import { MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'

export function useDishPage() {
  const dishStore = useDishStore()
  const userStore = useUserStore()
  const locationStore = useLocationStore()

  const dishId = ref(0)
  const dish = computed(() => dishStore.currentDish)
  const reviewList = computed(() => dishStore.reviewList)
  const reviewTotal = computed(() => dishStore.reviewTotal)
  const currentUserId = computed(() => userStore.userInfo?.id)

  /** detail-modular-review-cleanup：评价卡内触底分页（不再跳转独立全部评价页） */
  const reviewPage = ref(1)
  const reviewFinished = ref(false)
  const reviewLoadingMore = ref(false)
  function resetReviewPaging() {
    reviewPage.value = 1
    reviewFinished.value = false
    reviewLoadingMore.value = false
  }
  async function onReviewsReachBottom() {
    if (!dish.value || reviewLoadingMore.value || reviewFinished.value) return
    const pageSize = 10
    reviewLoadingMore.value = true
    try {
      const res = await dishStore.fetchReviews(dishId.value, {
        sort: 'latest',
        page: reviewPage.value + 1,
        pageSize,
        append: true,
      })
      reviewPage.value += 1
      if ((res?.list || []).length < pageSize) reviewFinished.value = true
    } catch { /* 底部加载失败静默，后续滚动可重试 */ } finally { reviewLoadingMore.value = false }
  }

  // N07 修复：删除后延迟返回定时器句柄，离开页面时清理
  let navTimer: ReturnType<typeof setTimeout> | null = null
  onUnload(() => {
    if (navTimer) clearTimeout(navTimer)
    navTimer = null
  })

  /** 大图列表：优先 images，回退单图 */
  const heroImages = computed(() => {
    const d = dish.value
    if (!d) return []
    return (d.images && d.images.length > 0) ? d.images : [d.image]
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
  /** 页面级滚动同步（原内层 scroll-view @scroll 移除）：只驱动承接条/标题的 opacity，不再参与布局/位移 */
  onPageScroll((e: any) => {
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

  /** 位置文案：食堂 › 楼层 › 档口 › 窗口 */
  const locationText = computed(() => {
    const d = dish.value
    if (!d) return ''
    const nodes: string[] = []
    if (d.canteen) nodes.push(d.canteen)
    if (d.floor) nodes.push(String(d.floor))
    if (d.stallName) nodes.push(d.stallName)
    if (d.windowNo) nodes.push(`窗口 ${d.windowNo}`)
    return nodes.join(' › ') || '未知位置'
  })

  /** 距你距离（米）：前端本地计算；未定位为 null */
  const dishDistance = computed(() => {
    const d = dish.value
    if (!d) return null
    const loc = locationStore.location
    if (!loc || typeof d.latitude !== 'number' || typeof d.longitude !== 'number') return null
    return haversineMeters(loc, { lat: d.latitude, lng: d.longitude })
  })

  /** 距你文案 */
  const distText = computed(() => {
    const m = dishDistance.value
    if (m == null) return '未定位'
    return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`
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
      uni.showToast({ title: '缺少菜品ID', icon: 'none' })
      return
    }
    dishId.value = id
    dishStore.resetDishDetail()
    ensureLocation()
    loadDishData()
  })

  /** 从二级页返回时：脏标记置位才重拉（评价列表 + 综合评分） */
  onShow(() => {
    if (!dishId.value || !dish.value) return
    if (dishStore.reviewsDirty) {
      dishStore.reviewsDirty = false
      resetReviewPaging()
      dishStore.fetchReviews(dishId.value, { sort: 'latest', pageSize: 10 })
      dishStore.fetchDetail(dishId.value)
    }
  })

  /** 确保拿到用户坐标（会话级缓存，避免重复授权）；失败静默降级 */
  async function ensureLocation() {
    if (locationStore.location) return
    try {
      const loc = await getUserLocation()
      if (loc) locationStore.setLocation(loc)
    } catch (e) {
      // 静默
    }
  }

  /** 进入页面加载详情（addView 埋点失败静默） */
  async function loadDishData() {
    if (!dishId.value) return
    resetReviewPaging()
    addView(dishId.value)
    await Promise.all([
      dishStore.fetchDetail(dishId.value),
      dishStore.fetchReviews(dishId.value, { sort: 'latest', pageSize: 10 }),
    ])
    const d = dish.value
    if (d) {
      sharedDish.value = {
        id: d.id,
        name: d.name,
        price: d.price,
        stallId: d.stallId,
        canteen: d.canteen,
        stallName: d.stallName,
      }
    } else {
      sharedDish.value = null
    }
  }

  onShareAppMessage(() => ({
    title: dish.value ? `${dish.value.name} ¥${dish.value.price}` : '菜品详情',
    path: dishDetailUrl(dishId.value),
  }))

  /** 距你未定位时点击：主动引导开启定位 */
  async function onDistTap() {
    if (dishDistance.value != null) return
    const before = locationStore.location
    await ensureLocation()
    if (!before && locationStore.location) {
      uni.showToast({ title: '已开启定位', icon: 'none' })
    } else if (!locationStore.location) {
      uni.showToast({ title: '定位未开启', icon: 'none' })
    }
  }

  /** 删除本人菜品（长按菜名触发） */
  function onDishLongPress() {
    const d = dish.value
    if (!d) return
    if (!userStore.userInfo || (d.createdBy != null && d.createdBy !== userStore.userInfo.id)) return
    uni.showModal({
      title: '删除菜品',
      content: '确定删除你发布的这道菜品吗？删除后不可恢复。',
      success: async (res) => {
        if (res.confirm) {
          try {
            await deleteDish(d.id)
            uni.showToast({ title: '已删除', icon: 'none' })
            if (navTimer) clearTimeout(navTimer)
            navTimer = setTimeout(() => uni.navigateBack(), 600)
          } catch (e: any) {
            uni.showToast({ title: e.message || '删除失败', icon: 'none' })
          }
        }
      },
    })
  }

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
          await dishStore.fetchReviews(dishId.value, { sort: 'latest', pageSize: 10 })
          resetReviewPaging()
          dishStore.fetchDetail(dishId.value)
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      },
    })
  }

  /* ===== 写评价（底栏左钮 → ReviewComposer 底部抽屉；提交成功后重拉评价 + 综合评分） ===== */
  const composerOpen = ref(false)
  function openComposer() {
    composerOpen.value = true
  }
  function onOpenReviewComposer() {
    if (!dish.value) return
    // 与删除/举报同款：未认证先弹认证（AuthSheet），认证完成后回调重进本函数
    if (!userStore.requireAuth(() => onOpenReviewComposer())) return
    openComposer()
  }
  /** 提交成功：重置分页并重拉最新评价（sort=latest 新评置顶）+ 刷新综合评分分布 */
  function onReviewSubmitted() {
    resetReviewPaging()
    dishStore.fetchReviews(dishId.value, { sort: 'latest', pageSize: 10 })
    dishStore.fetchDetail(dishId.value)
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
      ? [{ key: 'delete', label: '删除评价', icon: 'delete', iconColor: 'var(--color-error)', textColor: 'var(--color-error)' }]
      : [{ key: 'report', label: '举报评价', icon: 'report', iconColor: 'var(--color-error)', textColor: 'var(--color-error)' }]
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
    distText,
    dishDistance,
    ratingDistribution,
    reviewList,
    reviewTotal,
    currentUserId,
    composerOpen,
    reportOpen,
    reportSubmitting,
    reviewMoreOpen,
    reviewMoreItems,
    backToHome,
    onDishLongPress,
    onDistTap,
    onDeleteReview,
    onReviewReport,
    onReviewMore,
    onReviewMoreSelect,
    onOpenReviewComposer,
    onReviewSubmitted,
    submitReport,
  }
}
