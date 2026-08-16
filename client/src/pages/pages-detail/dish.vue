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
          <view class="skeleton-line skeleton-rating"></view>
        </view>
        <view class="skeleton-metric">
          <view class="skeleton-metric-col"></view>
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
        <!-- 2. 菜品大图：横向撑满，高约屏宽 80%，圆角 -->
        <view class="hero-img">
          <ImageSwiper :images="heroImages" height="80vw" />
        </view>

        <!-- 3. 基本信息区 -->
        <CardSection>
          <view class="title-row">
            <text class="dish-name" @longpress="onDishLongPress" aria-label="菜品名称">{{ dish.name }}</text>
            <text class="feedback-link" @tap="openApply" role="button" aria-label="反馈菜品信息有误">信息有误？</text>
          </view>

          <!-- 评分：⭐ 4.8（234人评价） -->
          <view class="rating-line" v-if="dish.rating > 0">
            <IconSvg name="star-filled" :size="30" color="var(--color-star)" />
            <text class="rating-num">{{ dish.rating }}</text>
            <text class="rating-count">（{{ dish.ratingCount }}人评价）</text>
          </view>

          <!-- 价格：品牌色；有折扣时原价删除线 -->
          <view class="price-row">
            <block v-if="hasPromo">
              <text class="promo-price">¥{{ dish.promoPrice }}</text>
              <text class="origin-price">¥{{ dish.originalPrice }}</text>
              <text class="promo-tag"><IconSvg name="clock" :size="22" color="var(--text-white)" /> 限时优惠</text>
            </block>
            <text v-else class="price-text">¥{{ dish.price }}</text>
          </view>

          <view class="tag-row" v-if="dishTagList.length > 0">
            <TagLabel v-for="tag in dishTagList" :key="tag" :text="tag" />
          </view>

          <view class="desc-row" v-if="dish.description">
            <text class="desc-content">{{ dish.description }}</text>
          </view>
        </CardSection>

        <!-- 4. 指标信息条：横向滑动，距你(首项) / 评分 / 评价 / 口味 / 地域 -->
        <scroll-view class="metric-scroll" scroll-x :enable-flex="true" :scroll-with-animation="!reduceMotion" :enhanced="true" :show-scrollbar="false" aria-label="菜品关键指标">
          <view class="metric-strip">
            <view class="metric-col metric-col--lead">
              <view class="metric-inner">
                <IconSvg v-if="dish.distance != null" name="location" :size="22" color="var(--color-primary)" class="metric-icon" />
                <text class="metric-value metric-value--lead">{{ dish.distance != null ? distText : '-' }}</text>
                <text class="metric-label">距你</text>
              </view>
            </view>
            <view class="metric-divider" />
            <view class="metric-col">
              <view class="metric-inner">
                <text class="metric-value">{{ dish.rating > 0 ? dish.rating.toFixed(1) : '-' }}</text>
                <text class="metric-label">评分</text>
              </view>
            </view>
            <view class="metric-divider" />
            <view class="metric-col">
              <view class="metric-inner">
                <text class="metric-value">{{ dish.ratingCount > 0 ? dish.ratingCount : '-' }}</text>
                <text class="metric-label">评价</text>
              </view>
            </view>
            <view class="metric-divider" />
            <view class="metric-col">
              <view class="metric-inner">
                <text class="metric-value">{{ spiceText }}</text>
                <text class="metric-label">口味</text>
              </view>
            </view>
            <view class="metric-divider" />
            <view class="metric-col">
              <view class="metric-inner">
                <text class="metric-value">{{ regionText }}</text>
                <text class="metric-label">地域</text>
              </view>
            </view>
          </view>
        </scroll-view>

        <!-- 5. 来源信息区（位置链路，不含距离，距离已上提到指标条） -->
        <CardSection>
          <view class="loc-row" aria-label="所在位置">
            <IconSvg name="location" :size="26" color="var(--color-primary)" class="loc-icon" />
            <text class="loc-text">{{ locationText }}</text>
          </view>
        </CardSection>

        <!-- 6. 综合评分卡：小标题 → 超大评分 → 5→1 星占比进度条 -->
        <CardSection v-if="dish.ratingCount > 0">
          <view class="summary-header">
            <text class="summary-title">综合评分</text>
          </view>
          <view class="summary-body">
            <view class="summary-left">
              <text class="summary-score">{{ dish.rating > 0 ? dish.rating.toFixed(1) : '-' }}</text>
              <text class="summary-base">满分 5.0</text>
            </view>
            <view class="summary-right">
              <view class="dist-item" v-for="item in ratingDistribution" :key="item.star">
                <text class="dist-star">{{ item.star }}星</text>
                <view class="dist-bar">
                  <view class="dist-fill" :style="{ width: distPct(item.count) }" />
                </view>
                <text class="dist-count">{{ item.count }}</text>
              </view>
            </view>
          </view>
        </CardSection>

        <!-- 6. 评价列表：默认最近三条 -->
        <view class="review-section">
          <view class="review-head-row">
            <text class="sec-title">评价 ({{ reviewTotal }})</text>
          </view>
          <view class="review-list" v-if="reviewList.length > 0">
            <ReviewItem
              v-for="rv in reviewList.slice(0, 3)"
              :key="rv.id"
              :review="rv"
              hide-useful
              :deletable="rv.userId === currentUserId"
              @delete="onDeleteReview"
            />
          </view>
          <EmptyState v-else text="还没有人评价过这道菜，来做第一个吧" icon="comment" />
          <view class="view-all" v-if="reviewList.length > 0" @tap="goReviewList" role="button" aria-label="查看全部评价">
            <text class="view-all-text">查看全部评价</text>
            <IconSvg name="arrow" :size="26" color="var(--text-secondary)" />
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
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { addView, deleteDish } from '@/api/dish'
import { deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
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

const theme = useThemeStore()
const dishStore = useDishStore()
const userStore = useUserStore()

const dishId = ref(0)
const dish = computed(() => dishStore.currentDish)
const reviewList = computed(() => dishStore.reviewList)
const reviewTotal = computed(() => dishStore.reviewTotal)
const currentDishId = computed(() => dishId.value)
const currentUserId = computed(() => userStore.userInfo?.id)

/** reduced-motion 降级 */
const reduceMotion = ref(false)
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

/** 距你文案：米/公里自适应 */
const distText = computed(() => {
  const m = dish.value?.distance
  if (m == null) return '-'
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
  dishStore.currentDish = null
  dishStore.reviewList = []
  dishStore.reviewTotal = 0
  loadDishData()
})

/** 进入页面加载详情（addView 埋点失败静默） */
async function loadDishData() {
  if (!dishId.value) return
  addView(dishId.value)
  await Promise.all([
    dishStore.fetchDetail(dishId.value),
    dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 3 }),
  ])
  // 记录待分享菜品，供 onShareAppMessage 生成卡片
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
          setTimeout(() => uni.navigateBack(), 600)
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
  })
}

/** 删除本人评价 */
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
        dishStore.reviewList = dishStore.reviewList.filter(x => x.id !== rv.id)
        dishStore.reviewTotal = Math.max(0, dishStore.reviewTotal - 1)
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
</script>

<style scoped>
.dish-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }

.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; }

/* ===== 内容样式 ===== */
/* 2. 大图：横向撑满，圆角 */
.hero-img { width: 100%; border-radius: var(--radius-card); overflow: hidden; line-height: 0; }

/* 3. 基本信息 */
.title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.dish-name { font-size: var(--font-h1); font-weight: var(--weight-heavy); letter-spacing: var(--tracking-h1); line-height: 1.2; color: var(--text-primary); flex: 1; min-width: 0; }
.feedback-link { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; min-height: 44px; display: flex; align-items: center; padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity 120ms ease, background-color 120ms ease; -webkit-tap-highlight-color: transparent; }
.feedback-link:active { opacity: 0.55; background-color: var(--bg-soft); }
.rating-line { display: flex; align-items: baseline; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.rating-num { font-size: var(--font-h3); font-weight: var(--weight-heavy); color: var(--text-primary); font-variant-numeric: tabular-nums; }
.rating-count { font-size: var(--font-small); color: var(--text-tertiary); }
.price-row { display: flex; align-items: baseline; gap: var(--spacing-xs); flex-wrap: wrap; margin-top: var(--spacing-sm); }
.price-text { font-size: var(--font-h2); font-weight: var(--weight-bold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.promo-price { font-size: var(--font-h2); font-weight: var(--weight-heavy); color: var(--color-error); font-variant-numeric: tabular-nums; }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
.promo-tag { font-size: var(--font-tiny); font-weight: var(--weight-bold); color: var(--text-white); background: var(--color-error); padding: 0 var(--spacing-xs); border-radius: var(--radius-icon); display: inline-flex; align-items: center; gap: var(--spacing-xs); }
.tag-row { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.desc-row { margin-top: var(--spacing-sm); }
.desc-content { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; }

/* 4. 来源信息区 */
.loc-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.loc-icon { width: 26rpx; height: 26rpx; line-height: 1; flex-shrink: 0; }
.loc-text { flex: 1; min-width: 0; font-size: var(--font-small); color: var(--text-secondary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 指标信息条：横向滑动，竖线分隔，首项为距你 */
.metric-scroll { margin-top: var(--spacing-md); width: 100%; -webkit-overflow-scrolling: touch; }
.metric-strip { display: flex; flex-direction: row; flex-wrap: nowrap; align-items: stretch; padding: 0 var(--spacing-md); }
.metric-col { flex: 0 0 25%; width: 25%; display: flex; align-items: center; justify-content: center; box-sizing: border-box; }
.metric-col--lead { flex: 0 0 22%; width: 22%; justify-content: flex-start; }
.metric-inner { display: flex; flex-direction: column; align-items: center; line-height: 1.2; }
.metric-col--lead .metric-inner { align-items: flex-start; }
.metric-icon { width: 22rpx; height: 22rpx; line-height: 1; margin-bottom: 6rpx; }
.metric-value { font-size: var(--font-h3); font-weight: var(--weight-heavy); color: var(--text-primary); font-variant-numeric: tabular-nums; }
.metric-value--lead { color: var(--color-primary); }
.metric-label { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: 4rpx; }
.metric-divider { width: 2rpx; align-self: stretch; background: var(--border-color); flex: 0 0 auto; }

/* 5. 综合评分卡：小标题 → 超大评分 → 5→1 星占比进度条 */
.summary-header { display: flex; align-items: center; margin-bottom: var(--spacing-sm); }
.summary-title { font-size: var(--font-small); font-weight: var(--weight-bold); color: var(--text-secondary); letter-spacing: var(--tracking-h3); }
.summary-body { display: flex; align-items: center; gap: var(--spacing-md); }
.summary-left { flex: 0 0 160rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.summary-score { font-size: var(--font-display, 64rpx); font-weight: var(--weight-heavy); color: var(--color-star); line-height: 1; font-variant-numeric: tabular-nums; }
.summary-base { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: 6rpx; }
.summary-right { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.rate-dist { display: flex; flex-direction: column; gap: var(--spacing-xs); }
.dist-item { display: flex; align-items: center; gap: var(--spacing-sm); }
.dist-star { width: 64rpx; flex-shrink: 0; font-size: var(--font-aux); color: var(--text-tertiary); text-align: right; }
.dist-bar { flex: 1; height: 12rpx; border-radius: var(--radius-pill, 999rpx); background: var(--bg-soft); overflow: hidden; }
.dist-fill { height: 100%; border-radius: var(--radius-pill, 999rpx); background: var(--color-star); transition: width 400ms var(--ease-out); }
.dist-count { width: 56rpx; flex-shrink: 0; font-size: var(--font-aux); color: var(--text-tertiary); text-align: left; font-variant-numeric: tabular-nums; }

/* 6. 评价列表 */
.review-section { margin: var(--spacing-md) var(--spacing-md) 0; padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card-soft); }
.review-head-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--spacing-xs); }
.review-list { margin-top: var(--spacing-xs); }
.view-all { display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); padding: var(--spacing-sm); border-radius: var(--radius-tag); background: var(--bg-soft); transition: opacity 120ms ease; -webkit-tap-highlight-color: transparent; }
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
.skeleton-metric { display: flex; gap: var(--spacing-sm); margin: 0 var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); }
.skeleton-metric-col { flex: 1; height: 64rpx; border-radius: var(--radius-tag); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
.skeleton-tag { width: 96rpx; height: 36rpx; border-radius: var(--radius-tag); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }

@media (prefers-reduced-motion: reduce) {
  .skeleton-swiper, .skeleton-line, .skeleton-tag { animation: none; }
}
</style>
