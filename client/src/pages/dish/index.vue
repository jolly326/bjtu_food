<template>
  <view class="page dish-page">
    <Header title="菜品详情" @back="backToHome" />
    <scroll-view class="scroll-wrap" scroll-y :scroll-with-animation="false" ref="mainRef" tabindex="-1" @scrolltolower="onReviewsReachBottom">
      <!-- 加载/不存在态统一由 StateView 一次承载（ui-feed-loading：空态复用 EmptyState，不在详情区重复放置加载态） -->
      <StateView v-if="!dish" :loading="dishStore.loading" :empty="true" empty-text="菜品不存在或已下架" empty-icon="empty" />

      <template v-else>
        <!-- 菜品大图：横向撑满，高约屏宽 44%（约屏高 1/4），圆角 -->
        <view class="hero-img">
          <ImageSwiper :images="heroImages" height="44vw" :indicator-dots="true" />
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
import { ref, computed, nextTick } from 'vue'
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
import ImageSwiper from '@/components/ImageSwiper.vue'
import StateView from '@/components/StateView.vue'
import Header from '@/components/AppHeader.vue'
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

/* 菜品大图 */
.hero-img { width: 100%; border-radius: var(--radius-card); overflow: hidden; line-height: 0; margin-top: var(--spacing-md); }
/* 首卡与头图衔接：头图外容器首卡自身 CardSection 带边距，无额外处理 */

/* 底部固定操作栏 */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 50; display: flex; align-items: center; padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
.share-btn-native { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; text-align: center; border-radius: var(--radius-btn); background: var(--color-primary); color: var(--color-on-primary); font-size: var(--font-subtitle); font-weight: var(--weight-medium); border: none; padding: 0; box-shadow: var(--shadow-float); }
.share-btn-native::after { border: none; }
</style>
