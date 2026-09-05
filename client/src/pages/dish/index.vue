<template>
  <view class="page dish-page">
    <!-- dish-detail-visual-polish：页面内覆盖导航（顶部透明 → 滚动渐显菜名与底白），大图全幅出血 -->
    <view
      class="dish-nav"
      :class="{ solid: navSolid }"
      :style="{ paddingTop: topPad, '--nav-h': navBarHeight + 'px', '--nav-pad-right': navPadRight, '--top-pad': topPad }"
    >
      <!-- 1.6 顶部渐变 scrim：状态栏+胶囊高度区叠透明→微暗渐变，给微信胶囊区衬底、提升返回钮对比度；
           随导航渐显反向淡出（1 - navOpacity），导航变实底后由实底接管，零滚动模型变动 -->
      <view class="dish-nav-scrim" :style="{ opacity: 1 - navOpacity }" />
      <view class="dish-nav-row">
        <view class="dish-nav-back" role="button" aria-label="返回" @tap="backToHome">
          <IconSvg
            name="arrow-left"
            :size="'22px'"
            :color="navSolid ? 'var(--text-primary)' : '#FFFFFF'"
            class="dish-nav-back-icon"
          />
        </view>
        <text class="dish-nav-title" :style="{ opacity: dish ? navOpacity : 1 }">{{ dishName }}</text>
      </view>
    </view>

    <scroll-view
      class="scroll-wrap"
      scroll-y
      :scroll-with-animation="false"
      ref="mainRef"
      tabindex="-1"
      @scroll="onNavScroll"
      @scrolltolower="onReviewsReachBottom"
    >
      <!-- 加载/不存在态统一由 StateView 一次承载（ui-feed-loading：空态复用 EmptyState，不在详情区重复放置加载态） -->
      <StateView v-if="!dish" :loading="dishStore.loading" :empty="true" empty-text="菜品不存在或已下架" empty-icon="empty" />

      <template v-else>
        <!-- 菜品大图：全幅出血（顶部贴屏顶，仅底部圆角裁切），高约屏高 1/4 -->
        <view class="hero-wrap">
          <ImageSwiper
            :images="heroImages"
            height="26vh"
            :indicator-dots="true"
            :placeholder-size="96"
            placeholder-background="var(--bg-card)"
          />
        </view>

        <!-- 私有组件编排：基本信息 / 综合评分（只读）/ 评价（卡内触底加载） -->
        <DishInfoCard
          :dish="dish"
          :location-text="locationText"
          :dist-text="distText"
          :dist-active="dishDistance != null"
          @dish-longpress="onDishLongPress"
          @dist-tap="onDistTap"
        />
        <DishSummaryCard
          :rating="dish.rating || 0"
          :rating-count="dish.ratingCount || 0"
          :distribution="ratingDistribution"
        />
        <DishReviewSection
          :reviews="reviewList"
          :total="reviewTotal"
          :current-user-id="currentUserId"
          :loading-more="reviewLoadingMore"
          :finished="reviewFinished"
          @delete="onDeleteReview"
          @report="onReviewReport"
          @more="onReviewMore"
        />

        <!-- 「没有更多了」在评价卡下方居中弱化（dish-detail-visual-polish） -->
        <view v-if="reviewFinished && reviewList.length > 0" class="reviews-end">没有更多了</view>

        <view style="height: calc(var(--spacing-lg) + 160rpx)" />
      </template>
    </scroll-view>

    <!-- 底部固定操作栏：分享占满 -->
    <view class="action-bar" v-if="dish">
      <button class="share-btn-native" open-type="share">分享给同学</button>
    </view>

    <!-- 举报弹窗（共享组件） -->
    <ReportModal
      :open="reportOpen"
      title="举报评价"
      placeholder="请描述举报原因…"
      confirm-text="提交举报"
      :submitting="reportSubmitting"
      @update:open="reportOpen = $event"
      @submit="submitReport"
    />

    <!-- 评价三点菜单：删除/举报（与动态卡一致：点击直接弹层，删除/举报动作内部再要求登录） -->
    <ReviewActionSheet
      :open="reviewMoreOpen"
      :is-own="reviewMoreIsOwn"
      @update:open="reviewMoreOpen = $event"
      @delete="onReviewMoreDelete"
      @report="onReviewMoreReport"
    />

    <!-- 认证弹层：点赞等需认证入口统一底部弹出 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { onLoad, onShow, onUnload, onShareAppMessage } from '@dcloudio/uni-app'
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
import ImageSwiper from '@/components/ImageSwiper.vue'
import StateView from '@/components/StateView.vue'
import ReportModal from '@/components/ReportModal.vue'
import ReviewActionSheet from '@/components/ReviewActionSheet.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import DishInfoCard from './DishInfoCard.vue'
import DishSummaryCard from './DishSummaryCard.vue'
import DishReviewSection from './DishReviewSection.vue'

const dishStore = useDishStore()
const userStore = useUserStore()
const locationStore = useLocationStore()

const dishId = ref(0)
const dish = computed(() => dishStore.currentDish)
const reviewList = computed(() => dishStore.reviewList)
const reviewTotal = computed(() => dishStore.reviewTotal)
const currentDishId = computed(() => dishId.value)
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
      isWithImage: false,
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

// 主内容区引用（2.4 路由切换聚焦，H5/桌面生效）
const mainRef = ref<any>()

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
/** 大图高度（px）：与 .hero-wrap 内 ImageSwiper 的 26vh 对应，用于推算渐显起点 */
const heroH = ref(0)
/** 右上角原生胶囊避让：与 AppHeader 同款计算（screenW − menuBtn.left + 8px），仅微信端生效 */
const rightPad = ref(0)
const topPad = computed(() => `max(${statusBarHeight.value}px, env(safe-area-inset-top))`)
/** 导航行右侧安全留白：避让微信胶囊，长菜名省略于胶囊左侧 */
const navPadRight = computed(() =>
  rightPad.value > 0 ? `calc(env(safe-area-inset-right, 0px) + ${rightPad.value}px)` : '0px',
)
/**
 * 渐显区间（px）：延迟到「信息卡菜名滚出可视区」之后才开始淡入覆盖导航标题，
 * 避免与信息卡自身的菜名在屏内同屏重复（dish-detail-visual-polish 1.5）。
 * 信息卡菜名位于大图之下（page-y ≈ heroH + 卡片顶距），当其整行滚到导航栏底之下时再淡入。
 */
const NAV_FADE_START = computed(() =>
  Math.max(8, heroH.value + 40 - (statusBarHeight.value + navBarHeight.value)),
)
const NAV_FADE_END = computed(() => NAV_FADE_START.value + 96)
const navOpacity = computed(() => {
  if (dish.value == null) return 1
  const p = (scrollTop.value - NAV_FADE_START.value) / (NAV_FADE_END.value - NAV_FADE_START.value)
  return Math.min(1, Math.max(0, p))
})
const navSolid = computed(() => dish.value == null || navOpacity.value >= 1)
const dishName = computed(() => (dish.value ? dish.value.name : '菜品详情'))
function onNavScroll(e: any) {
  scrollTop.value = e?.detail?.scrollTop || 0
}
onMounted(() => {
  // 与 AppHeader 同款导航尺寸计算（自定义导航 + 右上角胶囊避让）
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const w: any = (globalThis as any).wx
  const win = w ? (w.getWindowInfo ? w.getWindowInfo() : (w.getSystemInfoSync ? w.getSystemInfoSync() : null)) : null
  const sb = (win && win.statusBarHeight) || 20
  statusBarHeight.value = sb
  const mb = w && w.getMenuButtonBoundingClientRect ? w.getMenuButtonBoundingClientRect() : null
  if (mb && mb.height) {
    navBarHeight.value = getNavBarHeight(sb, mb)
    // 大图按 26vh 推算（miniprogram vh 基于屏幕高），用于延迟渐显起点
    const screenH = (win && (win.screenHeight || win.windowHeight)) || 667
    heroH.value = screenH * 0.26
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
  // #ifdef H5
  nextTick(() => mainRef.value?.$el?.focus?.())
  // #endif
  if (dishStore.reviewsDirty) {
    dishStore.reviewsDirty = false
    resetReviewPaging()
    dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 10 })
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
    dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 10 }),
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
  path: `/pages/dish/index?id=${dishId.value}`,
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
    confirmColor: '#FF3B30',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(rv.id)
        uni.showToast({ title: '评价已删除', icon: 'none' })
        await dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 10 })
        resetReviewPaging()
        dishStore.fetchDetail(dishId.value)
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

/* ===== 评价三点菜单（ReviewItem @more → 页面级 ReviewActionSheet） ===== */
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
function onReviewMoreDelete() {
  if (reviewMoreTarget.value) onDeleteReview(reviewMoreTarget.value)
}
function onReviewMoreReport() {
  if (reviewMoreTarget.value) onReviewReport(reviewMoreTarget.value)
}

/* ===== 评价举报（收敛到 useReport hook） ===== */
const { reportOpen, reportSubmitting, openReport, submitReport } =
  useReport({ type: 'review', title: '举报评价', placeholder: '请描述举报原因…' })

function onReviewReport(rv: Review) {
  openReport(rv.id)
}
</script>

<style scoped>
.dish-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; padding-bottom: calc(120rpx + env(safe-area-inset-bottom)); }

/* ===== dish-detail-visual-polish：覆盖导航 ===== */
.dish-nav {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  z-index: 80;
  background: transparent;
  transition: background var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
}
.dish-nav.solid {
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-color);
  box-shadow: var(--shadow-bar-soft);
}
/* 顶部渐变 scrim：覆盖状态栏+胶囊高度区，向上略深、向下渐隐，纯展示不拦截触摸 */
.dish-nav-scrim {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: calc(var(--top-pad) + var(--nav-h) + 36px);
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.30) 0%, rgba(0, 0, 0, 0.12) 46%, rgba(0, 0, 0, 0) 100%);
  pointer-events: none;
  z-index: 0;
}
.dish-nav-row {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  height: var(--nav-h);
}
.dish-nav-back {
  position: absolute;
  left: var(--spacing-sm);
  top: 0;
  bottom: 0;
  width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
/* 大图态：返回图标加半透深色圆底保证可见；非大图态透明白底黑图标 */
.dish-nav-back::before {
  content: '';
  position: absolute;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: var(--overlay-dark-faint);
  transition: background var(--duration-fast) var(--ease-out);
}
.dish-nav.solid .dish-nav-back::before { background: transparent; }
.dish-nav-back-icon { line-height: 1; }
.dish-nav-title {
  position: absolute;
  /* 1.4 胶囊避让：标题左侧起于返回钮之后，右侧止于微信胶囊左侧（--nav-pad-right），
     长菜名被胶囊截断并省略，绝不进入胶囊区；短菜名在 [返回钮, 胶囊] 间居中 */
  left: 60px;
  right: var(--nav-pad-right, 0px);
  max-width: none;
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: opacity var(--duration-base) var(--ease-out);
}

/* 菜品大图：全幅出血（顶部贴屏顶、左右撑满、仅底部圆角），图片底部圆角裁切 */
.hero-wrap { width: 100%; border-radius: 0 0 var(--radius-card) var(--radius-card); overflow: hidden; line-height: 0; }

/* 「没有更多了」位于评价卡下方居中弱化 */
.reviews-end { text-align: center; font-size: var(--font-small); color: var(--text-tertiary); padding: var(--spacing-md) 0 var(--spacing-2xs); }

/* 底部固定操作栏 */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 50; display: flex; align-items: center; padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
/* dish-detail-visual-polish：与全局主按钮统一（圆角 12px=24rpx 就近落地、600 字重、极淡下投影） */
.share-btn-native { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; text-align: center; border-radius: 24rpx; background: var(--color-primary); color: var(--color-on-primary); font-size: var(--font-subtitle); font-weight: var(--weight-semibold); border: none; padding: 0; box-shadow: var(--shadow-float); }
.share-btn-native::after { border: none; }
</style>
