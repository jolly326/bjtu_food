<template>
  <view class="page detail-page">
    <Header title="菜品详情" showBack>
      <template #action>
        <view class="share-btn" @tap="onShare">
          <IconSvg name="share" :size="40" color="var(--text-white)" />
        </view>
      </template>
    </Header>
    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
    >
      <template v-if="dishStore.loading">
        <view class="dish-skeleton">
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
          <view class="skeleton-card">
            <view class="skeleton-line skeleton-block"></view>
            <view class="skeleton-line skeleton-block"></view>
            <view class="skeleton-line skeleton-block short"></view>
          </view>
        </view>
      </template>
      <template v-else-if="dish">
        <ImageSwiper :images="dish.images || [dish.image]" />

        <CardSection>
          <view class="title-row" @longpress="onDishLongPress">
            <text class="dish-name">{{ dish.name }}</text>
            <view class="price-box">
              <block v-if="hasPromo">
                <text class="promo-price">¥{{ dish.promoPrice }}</text>
                <text class="origin-price">¥{{ dish.originalPrice }}</text>
                <text class="promo-tag"><IconSvg name="clock" :size="22" color="#E67E22" /> 限时</text>
              </block>
              <text v-else class="price-text">¥{{ dish.price }}</text>
            </view>
          </view>

          <view class="tag-row" v-if="dishTagList.length > 0">
            <TagLabel v-for="tag in dishTagList" :key="tag" :text="tag" />
          </view>

          <view class="rating-row">
            <Rating :model-value="dish.rating" readonly :star-size="28" />
          </view>
        </CardSection>

        <!-- ===== 合并卡片：位置与营业 / 菜品属性 / 菜品介绍，单卡内有序分区 ===== -->
        <CardSection title="菜品信息">
          <!-- 分区一：位置与营业（统一属性行：图标 + 标签 + 值） -->
          <view class="info-block">
            <view class="info-row info-row-tap" @tap="goToCanteen">
              <IconSvg name="location" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
              <text class="info-row-label">所在位置</text>
              <text class="info-row-value">{{ locationText }}</text>
              <text class="info-row-arrow">›</text>
            </view>
            <view class="info-row info-row-tap" v-if="dish.businessHours" @tap="goToStall">
              <IconSvg name="clock" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
              <text class="info-row-label">营业时段</text>
              <text class="info-row-value">{{ dish.businessHours }}</text>
              <text class="info-row-arrow">›</text>
            </view>
          </view>

          <!-- 分区二：菜品属性（辣度 / 分量 / 限量 / 供应时段，统一属性行） -->
          <view class="info-block info-block-divider" v-if="attrTags.length > 0">
            <view class="info-row" v-if="spiceLabel">
              <IconSvg name="chili" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
              <text class="info-row-label">辣度</text>
              <text class="info-row-value">{{ spiceLevelText }}</text>
            </view>
            <view class="info-row" v-if="portionLabel">
              <IconSvg name="portion" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
              <text class="info-row-label">分量</text>
              <text class="info-row-value">{{ portionLevelText }}</text>
            </view>
            <view class="info-row" v-if="dish.limited">
              <IconSvg name="clock" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
              <text class="info-row-label">供应</text>
              <text class="info-row-value">限量供应</text>
            </view>
            <view class="info-row" v-for="p in servePeriodLabels" :key="p">
              <IconSvg name="clock" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
              <text class="info-row-label">供应时段</text>
              <text class="info-row-value">{{ p }}</text>
            </view>
          </view>

          <!-- 分区三：菜品介绍（有则展示，单独成段并限制行高） -->
          <view class="info-block info-block-divider" v-if="dish.description">
            <text class="desc-content">{{ dish.description }}</text>
          </view>
        </CardSection>

        <!-- ===== 评价区 ===== -->
        <CardSection>
          <SectionTitle
            :title="`用户评价 (${reviewTotal})`"
            noMargin
            @tap="goToReviewList"
          />

          <view class="review-list" v-if="reviewList.length > 0">
            <ReviewItem
              v-for="rv in reviewList.slice(0, 3)"
              :key="rv.id"
              :review="rv"
            />
          </view>
          <EmptyState v-else text="暂无评价，来写第一条吧" />

          <view class="review-more-btn" v-if="reviewList.length > 0" @tap="goToReviewList">
            <text class="review-more-text">查看全部评价 ›</text>
          </view>
        </CardSection>

        <!-- 申请下架/纠错：不常用，降级为底部弱化的小文字链接，点击展开 Sheet -->
        <view class="apply-link" @tap="openApply">
          <text class="apply-link-text">反馈 / 申请下架 ›</text>
        </view>

      </template>
      <EmptyState v-else text="菜品不存在或已下架" />
      <view style="height: var(--spacing-lg)"></view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="action-bar" v-if="dish">
      <view class="fav-btn" :class="{ active: liked }" @tap="toggleLike">
        <IconSvg :name="liked ? 'heart-filled' : 'heart'" :size="40" :color="liked ? 'var(--color-like)' : 'var(--text-primary)'" class="fav-icon" />
        <text class="fav-text">{{ liked ? '已喜欢' : '喜欢' }}</text>
      </view>
      <view class="action-bar-btns">
        <AppButton text="写评价" type="outline" width="220rpx" margin="0 16rpx 0 0" @click="goToReview" />
        <AppButton text="去档口" width="240rpx" margin="0" @click="goToStall" />
      </view>
    </view>

    <!-- 申请下架/纠错 Sheet（共享组件） -->
    <ApplySheet
      :open="applyOpen"
      entity-type="DISH"
      :entity-id="dishId"
      @update:open="applyOpen = $event"
    />

    <!-- 分享面板（简化：复制链接） -->
    <view v-if="shareOpen" class="sheet-mask" @tap="shareOpen = false"></view>
    <view class="share-sheet" :class="{ open: shareOpen }">
      <view class="sheet-head">
        <text class="sheet-title">分享菜品</text>
        <IconSvg name="close" :size="36" color="var(--text-tertiary)" class="sheet-close" @click="shareOpen = false" />
      </view>
      <view class="share-body">
        <view class="share-option" @tap="copyShareLink">
          <IconSvg name="share" :size="40" color="var(--color-primary)" />
          <text class="share-option-text">复制链接</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppButton from '@/components/AppButton.vue'
import IconSvg from '@/components/IconSvg.vue'
import Rating from '@/components/Rating.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { deleteDish } from '@/api/dish'
import { getImageUrl } from '@/utils/image'
import { SPICE_LEVELS, PORTION_LEVELS, SERVE_PERIOD_MAP } from '@/constants/categories'
import type { Review } from '@/types/review'

const dishStore = useDishStore()
const userStore = useUserStore()

const refresherTriggered = ref(false)
const dish = computed(() => dishStore.currentDish)
const reviewList = computed(() => dishStore.reviewList)
const reviewTotal = computed(() => dishStore.reviewTotal)
const dishId = computed(() => dish.value?.id ?? 0)
const liked = ref(false)

/** 折扣价展示：promoPrice 非空即视为有促销 */
const hasPromo = computed(() => !!dish.value?.promoPrice)

/** 标签列表：新菜 + 后端 tags（统一在标题下方展示） */
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

/** 辣度标签 */
const spiceLabel = computed(() => {
  const lv = dish.value?.spiceLevel
  if (lv == null) return ''
  return `辣度·${SPICE_LEVELS[lv] ?? '未知'}`
})
/** 分量标签 */
const portionLabel = computed(() => {
  const lv = dish.value?.portion
  if (lv == null) return ''
  return `分量·${PORTION_LEVELS[lv] ?? '未知'}`
})
/** 供应时段标签 */
const servePeriodLabels = computed(() => {
  const raw = dish.value?.servePeriod || ''
  if (!raw) return []
  return raw.split(',').map(s => s.trim()).filter(Boolean).map(key => SERVE_PERIOD_MAP[key] || key)
})
/** 属性标签聚合（用于 section 显隐） */
const attrTags = computed(() => [
  spiceLabel.value,
  portionLabel.value,
  dish.value?.limited ? 'limited' : '',
  ...servePeriodLabels.value,
].filter(Boolean))

/** 位置链文本（食堂 › 楼层 › 档口 › 窗口，缺省回落占位） */
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
/** 辣度纯文本（去掉「辣度·」前缀，作属性行值） */
const spiceLevelText = computed(() => spiceLabel.value.replace(/^辣度·/, ''))
/** 分量纯文本（去掉「分量·」前缀） */
const portionLevelText = computed(() => portionLabel.value.replace(/^分量·/, ''))

/** 整页喜欢态（乐观切换；未登录引导） */
async function toggleLike() {
  if (!userStore.requireAuth()) return
  liked.value = !liked.value
}

/** 分享（简化） */
const shareOpen = ref(false)
function onShare() {
  shareOpen.value = true
}
function copyShareLink() {
  const url = `/pages/pages-detail/dish?id=${dishId.value}`
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '链接已复制', icon: 'none' }),
  })
  shareOpen.value = false
}

/** 申请下架/纠错 Sheet */
const applyOpen = ref(false)
function openApply() {
  if (!userStore.requireAuth()) return
  applyOpen.value = true
}

/** 删除本人发布的菜品（仅 created_by 本人） */
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
          setTimeout(() => uni.navigateBack(), 500)
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
  })
}

function goToReview() {
  if (!userStore.requireAuth()) return
  uni.navigateTo({ url: `/pages/pages-detail/review?dishId=${dishId.value}` })
}

function goToReviewList() {
  uni.navigateTo({ url: `/pages/pages-detail/review-list?dishId=${dishId.value}` })
}

function goToStall() {
  if (dish.value) {
    dishStore.navParams.stallName = dish.value.stallName
    dishStore.navParams.canteen = dish.value.canteen
    uni.navigateTo({ url: '/pages/pages-detail/stall' })
  }
}

function goToCanteen() {
  if (dish.value?.canteen) {
    uni.navigateTo({ url: `/pages/pages-detail/canteen?canteen=${encodeURIComponent(dish.value.canteen)}` })
  }
}

let currentDishId = 0

async function loadDishData() {
  if (!currentDishId) return
  await Promise.all([
    dishStore.fetchDetail(currentDishId),
    dishStore.fetchReviews(currentDishId, { sort: 'latest', isWithImage: false }),
  ])
}

onLoad((query) => {
  if (query?.id) {
    currentDishId = Number(query.id)
    loadDishData()
  }
})

function onRefresh() {
  if (refresherTriggered.value || !currentDishId) return
  refresherTriggered.value = true
  loadDishData().finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.detail-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom)); }
.title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.dish-name { font-size: var(--font-h1); font-weight: 700; letter-spacing: -0.02em; line-height: 1.2; color: var(--text-primary); flex: 1; min-width: 0; }
.price-text { font-size: var(--font-h2); font-weight: 700; color: var(--color-price); flex-shrink: 0; }
.price-box { display: flex; align-items: baseline; gap: var(--spacing-xs); flex-shrink: 0; flex-wrap: wrap; justify-content: flex-end; }
.promo-price { font-size: var(--font-h2); font-weight: 800; color: var(--color-error); }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; }
.promo-tag { font-size: 20rpx; font-weight: 700; color: var(--text-white); background: var(--color-error); padding: 0 var(--spacing-xs); border-radius: var(--radius-icon); display: inline-flex; align-items: center; gap: 4rpx; }
.tag-row { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.rating-row { display: flex; align-items: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }

/* 统一属性行：图标 + 标签 + 值（右对齐） */
.info-row { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-xs) 0; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.info-row-tap:active { transform: scale(var(--press-scale)); }
.info-row-icon { width: 28rpx; height: 28rpx; line-height: 1; flex-shrink: 0; }
.info-row-label { flex-shrink: 0; font-size: var(--font-aux); color: var(--text-tertiary); font-weight: 600; }
.info-row-value { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); font-weight: 500; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.info-row-arrow { font-size: var(--icon-sm); color: var(--text-tertiary); flex-shrink: 0; }

.desc-content { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 4; overflow: hidden; }

/* 合并卡片内分区 */
.info-block { padding: 0; }
.info-block-divider { margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }

.review-list { margin-top: var(--spacing-sm); }
.review-more-btn { margin-top: var(--spacing-sm); display: flex; justify-content: center; }
.review-more-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; }

/* 申请入口：不常用，降级为底部弱化的小文字链接 */
.apply-link { display: flex; justify-content: center; padding: var(--spacing-md) 0 var(--spacing-sm); -webkit-tap-highlight-color: transparent; }
.apply-link:active { opacity: 0.6; }
.apply-link-text { font-size: var(--font-aux); color: var(--text-tertiary); }

/* 底部操作栏 */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--glass-highlight-soft); z-index: 50; }
.fav-btn { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 96rpx; min-width: 96rpx; gap: var(--spacing-xs); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.fav-btn:active { transform: scale(var(--press-scale)); }
.fav-icon { width: 40rpx; height: 40rpx; line-height: 1; flex-shrink: 0; }
.fav-text { font-size: 20rpx; color: var(--text-primary); white-space: nowrap; line-height: 1.2; }
.fav-btn.active .fav-text { color: var(--color-like); }
.action-bar-btns { flex: 1; display: flex; justify-content: flex-end; }

/* 分享 Sheet */
.sheet-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 90; }
.share-sheet { position: fixed; left: 0; right: 0; bottom: 0; background: var(--bg-card); border-radius: var(--radius-modal) var(--radius-modal) 0 0; box-shadow: var(--shadow-modal); z-index: 100; transform: translateY(100%); transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }
.share-sheet.open { transform: translateY(0); }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sheet-close { padding: 0 var(--spacing-xs); }
.share-body { padding: var(--spacing-md) var(--spacing-lg); }
.share-option { display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-sm) 0; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.share-option:active { transform: scale(0.97); }
.share-option-text { font-size: var(--font-body); color: var(--text-primary); font-weight: 600; }

@media (prefers-reduced-motion: reduce) {
  .share-sheet { transition: opacity 0.2s ease; }
}

/* 加载骨架（与 stall/canteen 风格一致，统一材质与呼吸动效） */
.dish-skeleton { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-swiper { width: 100%; height: 460rpx; border-radius: var(--radius-card); background: var(--bg-soft); animation: skeleton-pulse 1.2s ease-in-out infinite; }
.skeleton-card { background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-sm); }
.skeleton-line { height: 28rpx; border-radius: var(--radius-tag); background: var(--bg-soft); animation: skeleton-pulse 1.2s ease-in-out infinite; }
.skeleton-title { width: 60%; height: 40rpx; }
.skeleton-price { width: 36%; }
.skeleton-rating { width: 44%; }
.skeleton-block { height: 24rpx; }
.skeleton-block.short { width: 70%; }
.skeleton-tags { display: flex; gap: var(--spacing-xs); }
.skeleton-tag { width: 96rpx; height: 36rpx; border-radius: var(--radius-tag); background: var(--bg-soft); animation: skeleton-pulse 1.2s ease-in-out infinite; }
@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
@media (prefers-reduced-motion: reduce) {
  .skeleton-swiper, .skeleton-line, .skeleton-tag { animation: none; }
}
</style>
