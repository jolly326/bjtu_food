<template>
  <view class="page dish-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="菜品详情" @back="backToHome" />
    <scroll-view class="scroll-wrap" scroll-y :scroll-with-animation="!reduceMotion">
      <!-- 加载骨架屏 -->
      <view v-if="dishStore.loading && !dish" class="dish-skeleton">
        <view class="skeleton-swiper"></view>
        <view class="skeleton-card">
          <view class="skeleton-line skeleton-title"></view>
          <view class="skeleton-line skeleton-price"></view>
          <view class="skeleton-tags">
            <view class="skeleton-tag"></view>
            <view class="skeleton-tag"></view>
          </view>
          <view class="skeleton-line skeleton-loc"></view>
          <view class="skeleton-line skeleton-rating"></view>
        </view>
        <view class="skeleton-metric">
          <view class="skeleton-metric-col"></view>
          <view class="skeleton-metric-col"></view>
          <view class="skeleton-metric-col"></view>
          <view class="skeleton-metric-col"></view>
        </view>
        <view class="skeleton-card">
          <view class="skeleton-line skeleton-block"></view>
          <view class="skeleton-line skeleton-block"></view>
          <view class="skeleton-line skeleton-block short"></view>
        </view>
      </view>

      <template v-else-if="dish">
        <!-- 2. 菜品大图：横向撑满，高约屏宽 56%，圆角 -->
        <view class="hero-img">
          <ImageSwiper :images="heroImages" height="56vw" :indicator-dots="true" />
        </view>

        <!-- 3. 基本信息区：名称/标徽/价格 → 位置 → 简介 → 指标条 -->
        <CardSection>
          <!-- 第一行：名称 + 价格（标徽独立成行） -->
          <view class="title-row" @longpress="onDishLongPress">
            <text class="dish-name" aria-label="菜品名称">{{ dish.name }}</text>
            <view class="price-row">
              <block v-if="hasPromo">
                <text class="promo-price">¥{{ dish.promoPrice }}</text>
                <text class="origin-price">¥{{ dish.originalPrice }}</text>
                <text class="promo-tag"><IconSvg name="clock" :size="22" color="var(--text-white)" /> 限时优惠</text>
              </block>
              <text v-else class="price-text">¥{{ dish.price }}</text>
            </view>
          </view>

          <!-- 第二行：标徽（独立成行） -->
          <view class="tag-row tag-row--standalone" v-if="dishTagList.length > 0">
            <TagLabel v-for="tag in dishTagList" :key="tag" :text="tag" />
          </view>

          <!-- 第二行：位置 + 右侧距你 -->
          <view class="loc-row" aria-label="所在位置">
            <view class="loc-left">
              <IconSvg name="location" :size="26" color="var(--color-primary)" class="loc-icon" />
              <text class="loc-text">{{ locationText }}</text>
            </view>
            <view class="loc-dist" :class="dishDistance != null ? 'loc-dist--lead' : 'loc-dist--muted'" @tap="onDistTap" role="button" :aria-label="dishDistance != null ? '距你距离' : '开启定位查看距你多远'">
              <IconSvg v-if="dishDistance != null" name="location" :size="22" color="var(--color-primary)" class="loc-dist-icon" />
              <text class="loc-dist-text">{{ distText }}</text>
            </view>
          </view>

          <!-- 第三行：简介（展开按钮同行右侧，默认两行） -->
          <view class="desc-row" v-if="dish.description">
            <view class="desc-content-wrap">
              <text class="desc-content" :class="{ 'desc-content--collapsed': !descExpanded }">{{ dish.description }}</text>
            </view>
            <text class="desc-toggle" @tap="descExpanded = !descExpanded" role="button" aria-label="展开或收起简介">
              {{ descExpanded ? '收起' : '展开' }}
            </text>
          </view>

          <!-- 第四行：关键指标条（评分/评价/口味/地域，固定四列，缺省 - 占位） -->
          <view class="metric-panel" v-if="hasMetrics">
            <view class="metric-col">
              <text class="metric-val">{{ dish.rating > 0 ? dish.rating.toFixed(1) : '-' }}</text>
              <text class="metric-label">评分</text>
            </view>
            <view class="metric-col">
              <text class="metric-val">{{ dish.ratingCount > 0 ? formatCount(dish.ratingCount) : '-' }}</text>
              <text class="metric-label">评价</text>
            </view>
            <view class="metric-col">
              <text class="metric-val metric-val--text">{{ spiceText }}</text>
              <text class="metric-label">口味</text>
            </view>
            <view class="metric-col">
              <text class="metric-val metric-val--text">{{ regionText }}</text>
              <text class="metric-label">地域</text>
            </view>
          </view>
        </CardSection>

        <!-- 5. 综合评分卡：左大分数居中 + 右星级分布（星图标 + 星数 + 横条 + 数量；右上角写评价已移除，统一走评价卡头入口） -->
        <CardSection>
          <view class="summary-head">
            <text class="summary-head-title">综合评分</text>
          </view>
          <view v-if="dish.ratingCount > 0" class="summary-body">
            <view class="summary-left">
              <text class="summary-score">{{ dish.rating > 0 ? dish.rating.toFixed(1) : '-' }}</text>
              <text class="summary-count">{{ dish.ratingCount }} 人评分</text>
            </view>
            <view class="summary-right">
              <view class="dist-item" v-for="item in ratingDistribution" :key="item.star">
                <view class="dist-stars">
                  <IconSvg
                    v-for="n in 5"
                    :key="n"
                    name="star"
                    :size="20"
                    :color="n <= item.star ? 'var(--color-star)' : 'var(--color-star-empty)'"
                  />
                </view>
                <text class="dist-star-num">{{ item.star }}</text>
                <view class="dist-bar">
                  <view class="dist-fill" :style="{ width: distPct(item.count) }" />
                </view>
                <text class="dist-count">{{ item.count }}</text>
              </view>
            </view>
          </view>
          <view v-else class="summary-empty" @tap="goWriteReview" role="button" aria-label="写第一个评价">
            <text class="summary-empty-text">还没有评分</text>
          </view>
        </CardSection>

        <!-- 6. 评价卡片：整卡一张（卡头标题 + 卡内评价条目 + footer），与动态卡片形态趋同 -->
        <view class="review-section" id="review-section">
          <view class="review-card">
            <!-- 卡头：标题（无竖条）+ 写评价入口（唯一入口，字号加大避免过小） -->
            <view class="review-card-head">
              <text class="review-card-title">评价 ({{ reviewTotal }})</text>
              <view class="review-write" role="button" aria-label="写评价" @tap="goWriteReview">
                <IconSvg name="edit" :size="26" color="var(--color-primary)" />
                <text class="review-write-text">写评价</text>
              </view>
            </view>

            <!-- 卡内条目（flat 扁平，不重复卡片） -->
            <view class="review-list" v-if="reviewList.length > 0">
              <ReviewItem
                v-for="rv in reviewList.slice(0, 3)"
                :key="rv.id"
                :review="rv"
                :current-user-id="currentUserId"
                flat
                hide-useful
                @delete="onDeleteReview"
                @report="onReviewReport"
                @more="onReviewMore"
              />
            </view>
            <view v-else class="review-empty">
              <EmptyState text="还没有人评价过这道菜，来做第一个吧" icon="comment" />
            </view>

            <!-- 卡尾：查看全部 -->
            <view class="view-all" v-if="reviewList.length > 0" @tap="goReviewList" role="button" aria-label="查看全部评价">
              <text class="view-all-text">查看全部评价</text>
              <IconSvg name="arrow" :size="26" color="var(--text-secondary)" />
            </view>
          </view>
        </view>

        <view style="height: calc(var(--spacing-lg) + 160rpx)" />
      </template>

      <EmptyState v-else text="菜品不存在或已下架" icon="empty" />
    </scroll-view>

    <!-- 7. 底部固定操作栏：分享占满 -->
    <view class="action-bar" v-if="dish">
      <button class="share-btn-native" open-type="share">分享给同学</button>
    </view>

    <!-- 申请下架/纠错 Sheet（共享组件） -->
    <ApplySheet :open="applyOpen" entity-type="DISH" :entity-id="currentDishId" @update:open="applyOpen = $event" />

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

    <!-- 认证弹层：点赞等需认证入口统一底部弹出（z-index 300 高于 action-bar 50，不会被详情内容遮挡）。
         写评价入口已不在此拦截，认证在合一发布页提交时检测（publish-content submit） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onLoad, onShow, onUnload, onShareAppMessage } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { useLocationStore } from '@/stores/location'
import { haversineMeters, getUserLocation } from '@/utils/location'
import { addView, deleteDish } from '@/api/dish'
import { deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
import { useReport } from '@/composables/useReport'
import { sharedDish } from '@/utils/shareState'
import { backToHome } from '@/utils/nav'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import EmptyState from '@/components/EmptyState.vue'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import ReportModal from '@/components/ReportModal.vue'
import ReviewActionSheet from '@/components/ReviewActionSheet.vue'

const theme = useThemeStore()
const dishStore = useDishStore()
const userStore = useUserStore()
const locationStore = useLocationStore()

const dishId = ref(0)
const dish = computed(() => dishStore.currentDish)
const reviewList = computed(() => dishStore.reviewList)
const reviewTotal = computed(() => dishStore.reviewTotal)
const currentDishId = computed(() => dishId.value)

// N07 修复：删除后延迟返回定时器句柄，离开页面时清理，避免手动返回后多退一层
let navTimer: ReturnType<typeof setTimeout> | null = null
onUnload(() => {
  if (navTimer) clearTimeout(navTimer)
  navTimer = null
})
const currentUserId = computed(() => userStore.userInfo?.id)

/** reduced-motion 降级 */
const reduceMotion = ref(false)
/** 简介展开/收起 */
const descExpanded = ref(false)
if (typeof window !== 'undefined') {
  reduceMotion.value = !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

/** 大图列表：优先 images，回退单图 */
const heroImages = computed(() => {
  const d = dish.value
  if (!d) return []
  return (d.images && d.images.length > 0) ? d.images : [d.image]
})

/** 折扣价展示 */
const hasPromo = computed(() => !!dish.value?.promoPrice)

/** 评分分布：按星级 5→1 排序 */
const ratingDistribution = computed(() => {
  const list = (dish.value?.ratingDistribution || []).slice()
  list.sort((a, b) => b.star - a.star)
  return list
})
function distPct(count: number): string {
  const total = dish.value?.ratingCount || 0
  if (!total) return '0%'
  return `${Math.round((count / total) * 100)}%`
}

/** 标签列表 */
const dishTagList = computed(() => {
  const d = dish.value
  if (!d) return []
  const list: string[] = []
  if (d.isNew) list.push('新品')
  for (const t of d.tags) {
    if (!list.includes(t)) list.push(t)
  }
  return list
})

/** 位置文案：食堂 › 楼层 › 档口 › 窗口（仅展示，档口详情已下架） */
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

/** 地域（美食来源地）：后端联表回填，缺省显 - */
const regionText = computed(() => {
  const d = dish.value
  if (!d) return '-'
  return (d.region && d.region.trim()) || '-'
})

/** 口味文案：辣度枚举 0-3 → 不辣/微辣/中辣/重辣 */
const spiceText = computed(() => {
  const map: Record<number, string> = { 0: '不辣', 1: '微辣', 2: '中辣', 3: '重辣' }
  const lv = dish.value?.spiceLevel
  return lv != null && map[lv] ? map[lv] : '-'
})

/** 评价数格式化：<1万显原数，≥1万显 x.x万 */
function formatCount(n: number | undefined): string {
  const v = n || 0
  if (v >= 10000) return `${(v / 10000).toFixed(1)}万`
  return String(v)
}

/** 距你距离（米）：前端基于 locationStore 用户坐标 + Haversine 本地计算；服务器不算距离 */
const dishDistance = computed(() => {
  const d = dish.value
  if (!d) return null
  const loc = locationStore.location
  if (!loc || typeof d.latitude !== 'number' || typeof d.longitude !== 'number') return null
  return haversineMeters(loc, { lat: d.latitude, lng: d.longitude })
})

/** 距你文案：米/公里自适应；未定位时给轻提示 */
const distText = computed(() => {
  const m = dishDistance.value
  if (m == null) return '未定位'
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`
})

onLoad((query) => {
  const id = Number(query?.id)
  if (!id) {
    uni.showToast({ title: '缺少菜品ID', icon: 'none' })
    return
  }
  dishId.value = id
  // 进入新菜品前清空旧详情，避免闪现上一道菜（store 全局状态残留）
  dishStore.resetDishDetail()
  // 取定位：确保用户坐标（会话级缓存），详情页「距你」才能本地算距离
  ensureLocation()
  loadDishData()
})

/** 从写评价页返回时：仅当脏标记置位才重拉（评价列表 + 综合评分卡），避免每次返回无效请求（#8/#3） */
onShow(() => {
  if (!dishId.value || !dish.value) return
  if (dishStore.reviewsDirty) {
    dishStore.reviewsDirty = false
    dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 3 })
    // 写评价/回复后综合评分（分数、分布、评价数）需随详情刷新
    dishStore.fetchDetail(dishId.value)
  }
})

/** 确保拿到用户坐标（会话级缓存，避免重复授权）；失败静默降级（距你显 -） */
async function ensureLocation() {
  if (locationStore.location) return
  try {
    const loc = await getUserLocation()
    if (loc) locationStore.setLocation(loc)
  } catch (e) {
    // 用户拒绝授权 / 定位不可用：静默，距离降级为 -
  }
}

/** 进入页面加载详情（addView 埋点失败静默） */
async function loadDishData() {
  if (!dishId.value) return
  addView(dishId.value)
  await Promise.all([
    dishStore.fetchDetail(dishId.value),
    dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 3 }),
  ])
  // 记录待分享菜品，供 onShareAppMessage 生成卡片；加载失败/无详情时清空，避免分享沿用上一道菜
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
  path: `/pages/pages-detail/dish?id=${dishId.value}`,
}))

/** 申请下架/纠错 Sheet */
const applyOpen = ref(false)
function openApply() {
  applyOpen.value = true
}

/** 距你未定位时点击：主动引导开启定位，成功后自动重算距离 */
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

/** 删除本人评价/回复：成功后重拉列表（计数准确无漂移）+ 刷新综合评分卡 */
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
        // 删除后立即重拉列表 + 综合评分卡（计数准确无漂移），无需再置 reviewsDirty
        // （避免与 onShow 的重拉逻辑叠加导致重复请求 + addView 重复埋点）
        await dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 3 })
        dishStore.fetchDetail(dishId.value)
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

/** 查看全部评价 → 独立评价列表页 */
function goReviewList() {
  uni.navigateTo({ url: `/pages/pages-detail/review-list?dishId=${currentDishId.value}` })
}

/** 写评价入口：进入合一发布页（评价态：锁定所属菜品+默认5星），直接进入不打断编辑体验，认证在提交时检测 */
function goWriteReview() {
  uni.navigateTo({ url: `/pages/pages-user/publish-content/index?dishId=${currentDishId.value}&from=dish` })
}

/* ===== 评价三点菜单（ReviewItem @more → 页面级 ReviewActionSheet） ===== */
const reviewMoreOpen = ref(false)
const reviewMoreTarget = ref<Review | null>(null)
const reviewMoreIsOwn = computed(() => {
  const rv = reviewMoreTarget.value
  return rv != null && userStore.userInfo?.id != null && rv.userId === userStore.userInfo.id
})

/** 评价右上角三点：直接弹层（不先要求登录）；删除/举报动作内部再 requireAuth，与动态三点一致 */
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

/* ===== 评价举报（复用共享 ReportModal，收敛到 useReport hook） ===== */
const { reportOpen, reportSubmitting, openReport, submitReport } =
  useReport({ type: 'review', title: '举报评价', placeholder: '请描述举报原因…' })

function onReviewReport(rv: Review) {
  openReport(rv.id)
}

/** 指标条是否需要展示：菜品存在且至少有一项有效指标即显示 */
const hasMetrics = computed(() => {
  const d = dish.value
  if (!d) return false
  return d.rating > 0 || d.ratingCount > 0 || spiceText.value !== '-' || regionText.value !== '-'
})
</script>

<style scoped>
.dish-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }

.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; padding-bottom: calc(120rpx + env(safe-area-inset-bottom)); }

/* ===== 内容样式 ===== */
/* 2. 大图：横向撑满，圆角 */
.hero-img { width: 100%; border-radius: var(--radius-card); overflow: hidden; line-height: 0; }

/* 3. 基本信息 */
.title-row { display: flex; align-items: flex-start; flex-wrap: wrap; gap: var(--spacing-xs); }
.dish-name { font-size: var(--font-headline); font-weight: var(--weight-bold); letter-spacing: var(--tracking-h2); line-height: 1.2; color: var(--text-primary); flex: 0 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tag-row { display: flex; flex-wrap: nowrap; gap: var(--spacing-xs); flex: 0 0 auto; align-items: center; }
.price-row { display: flex; align-items: baseline; gap: var(--spacing-xs); flex-wrap: wrap; flex: 0 0 auto; margin-left: auto; }
.price-text { font-size: var(--font-h2); font-weight: var(--weight-bold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.promo-price { font-size: var(--font-h2); font-weight: var(--weight-heavy); color: var(--color-error); font-variant-numeric: tabular-nums; }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
.promo-tag { font-size: var(--font-tiny); font-weight: var(--weight-bold); color: var(--text-white); background: var(--color-error); padding: 0 var(--spacing-xs); border-radius: var(--radius-icon); display: inline-flex; align-items: center; gap: var(--spacing-xs); }
.desc-row { display: flex; align-items: flex-start; gap: var(--spacing-sm); margin-top: var(--spacing-md); }
.desc-content-wrap { flex: 1 1 auto; min-width: 0; }
.desc-content { font-size: var(--font-small); color: var(--text-secondary); line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; overflow: hidden; word-break: break-all; }
.desc-content--collapsed { -webkit-line-clamp: 2; }
.desc-toggle { flex: 0 0 auto; align-self: flex-start; font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); padding: 2rpx var(--spacing-xs); line-height: 1.4; -webkit-tap-highlight-color: transparent; }

/* 第三行：位置 + 距你 */
.loc-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); margin-top: var(--spacing-md); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); }
.loc-left { display: flex; align-items: center; gap: var(--spacing-xs); min-width: 0; flex: 1 1 auto; }
.loc-icon { width: 26rpx; height: 26rpx; line-height: 1; flex-shrink: 0; }
.loc-text { flex: 1; min-width: 0; font-size: var(--font-small); color: var(--text-secondary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.loc-dist { display: flex; align-items: center; gap: 6rpx; flex: 0 0 auto; margin-left: var(--spacing-sm); }
.loc-dist-icon { width: 22rpx; height: 22rpx; line-height: 1; flex-shrink: 0; }
.loc-dist-text { font-size: var(--font-small); font-weight: var(--weight-medium); }
.loc-dist--lead { color: var(--color-primary); }
.loc-dist--muted { color: var(--text-tertiary); }

/* 第二行：标徽独立成行时与位置行拉开间距 */
.tag-row--standalone { margin-top: var(--spacing-md); }

/* 第五行：关键指标条（评分/评价/口味/地域，无背景色，仅灰色竖线分隔） */
.metric-panel { display: flex; align-items: stretch; margin-top: var(--spacing-md); padding: var(--spacing-sm) 0; }
.metric-col { flex: 1 1 0; min-width: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; padding: 0 var(--spacing-sm); position: relative; }
.metric-col + .metric-col::before { content: ''; position: absolute; left: 0; top: 20%; bottom: 20%; width: 2rpx; background: var(--border-color); }
.metric-val { font-size: 32rpx; font-weight: var(--weight-bold); color: var(--text-primary); line-height: 1; font-variant-numeric: tabular-nums; display: inline-flex; align-items: baseline; gap: 4rpx; }
/* 口味/地域文本列：与数字列统一字号(32rpx)与主色，保留常规字重；长文本单行省略避免撑破四列 */
.metric-val--text { font-size: 32rpx; font-weight: var(--weight-medium); color: var(--text-primary); display: inline-block; line-height: 1; max-width: 100%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.metric-label { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1; }

/* 5. 综合评分卡：左大分数居中 + 右星级分布（星图标 + 星数 + 横条 + 数量） */
.summary-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--spacing-sm); }
.summary-head-title { font-size: var(--font-small); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.summary-body { display: flex; align-items: center; gap: var(--spacing-lg); }
/* 左栏：大分数 + 人数列组，垂直水平双居中 */
.summary-left { flex: 0 0 160rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--spacing-xs); }
/* 页面最强数字锚点：刻意高于 token 梯度（44/48rpx），与指标条数值拉开差距，不随缩放 */
.summary-score { font-size: 56rpx; font-weight: var(--weight-heavy); color: var(--text-primary); line-height: 1; font-variant-numeric: tabular-nums; }
.summary-count { font-size: var(--font-aux); color: var(--text-tertiary); }
.summary-right { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: var(--spacing-xs); }
.summary-empty { padding: var(--spacing-md) 0; text-align: center; }
.summary-empty-text { font-size: var(--font-small); color: var(--text-tertiary); }
.dist-item { display: flex; align-items: center; gap: var(--spacing-sm); }
.dist-stars { flex: 0 0 auto; display: flex; align-items: center; gap: 2rpx; }
.dist-star-num { flex: 0 0 auto; width: 28rpx; text-align: right; font-size: var(--font-aux); color: var(--text-primary); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; }
.dist-bar { flex: 1; min-width: 0; height: 12rpx; border-radius: var(--radius-pill, 999rpx); background: var(--color-star-empty); overflow: hidden; }
.dist-fill { height: 100%; border-radius: var(--radius-pill, 999rpx); background: var(--color-star); transition: width var(--duration-slow) var(--ease-out); }
.dist-count { flex: 0 0 auto; width: 48rpx; text-align: left; font-size: var(--font-aux); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }

/* 6. 评价卡片：整卡一张（卡头 + flat 条目 + footer），与动态卡片形态趋同、无竖条装饰 */
.review-section { margin: var(--spacing-md) var(--spacing-md) 0; }
.review-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-sm) var(--spacing-md);
}
.review-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) 0 var(--spacing-sm);
}
/* 卡头标题：与分区标题同视觉（h2 加重），无竖条 */
.review-card-title {
  font-size: var(--font-h2);
  font-weight: var(--weight-heavy);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h2);
  flex: 1;
  min-width: 0;
}
.review-write {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2xs);
  flex-shrink: 0;
  padding: var(--spacing-2xs) var(--spacing-sm);
  border-radius: var(--radius-tag);
  transition: opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.review-write:active { opacity: 0.6; }
.review-write-text { font-size: var(--font-card); color: var(--color-primary); font-weight: var(--weight-semibold); }
.review-list { display: flex; flex-direction: column; }
.review-empty { display: flex; flex-direction: column; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-lg) 0; }
.view-all {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  margin-top: var(--spacing-xs);
  padding: var(--spacing-sm) 0 var(--spacing-2xs);
  transition: opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.view-all:active { opacity: 0.6; }
.view-all-text { font-size: var(--font-small); color: var(--text-secondary); font-weight: var(--weight-semibold); }

/* 7. 底部固定操作栏 */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 50; display: flex; align-items: center; padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
.share-btn-native { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; text-align: center; border-radius: var(--radius-btn); background: var(--color-primary); color: var(--color-on-primary); font-size: var(--font-card); font-weight: var(--weight-medium); border: none; padding: 0; }
.share-btn-native::after { border: none; }

/* 加载骨架 */
.dish-skeleton { display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-swiper { width: 100%; height: 460rpx; border-radius: var(--radius-card); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
.skeleton-card { background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-sm); margin: 0 var(--spacing-md); }
.skeleton-line { height: 28rpx; border-radius: var(--radius-tag); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
.skeleton-title { width: 60%; height: 40rpx; }
.skeleton-price { width: 36%; }
.skeleton-rating { width: 44%; }
.skeleton-block { height: 24rpx; }
.skeleton-block.short { width: 70%; }
.skeleton-tags { display: flex; gap: var(--spacing-xs); }
.skeleton-metric { display: flex; gap: 0; margin: 0 var(--spacing-md); padding: var(--spacing-md) 0; background: var(--bg-card); border-radius: var(--radius-card); }
.skeleton-metric-col { flex: 0 0 25%; height: 64rpx; border-radius: var(--radius-tag); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
.skeleton-tag { width: 96rpx; height: 36rpx; border-radius: var(--radius-tag); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }

@media (prefers-reduced-motion: reduce) {
  .skeleton-swiper, .skeleton-line, .skeleton-tag { animation: none; }
  .dist-fill { transition: none; }
}
</style>
