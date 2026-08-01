<template>
  <view class="page stall-detail-page">
    <Header :title="stallDetail?.name || '档口'" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <!-- 加载骨架 -->
      <view v-if="loading" class="stall-skeleton">
        <view class="sk-swiper skeleton" />
        <view class="sk-info skeleton" />
        <view class="sk-dish" v-for="s in 3" :key="s">
          <view class="sk-dish-img skeleton" />
          <view class="sk-dish-body">
            <view class="sk-line skeleton" />
            <view class="sk-line sk-line-short skeleton" />
          </view>
        </view>
      </view>

      <template v-else-if="stallDetail">
        <ImageSwiper :images="stallDetail.images" />

        <!-- 档口信息（合并卡片，含位置、星级与简介） -->
        <CardSection title="档口信息">
          <view class="info-body">
            <view class="info-location">
              <IconSvg name="location" :size="26" color="var(--text-tertiary)" class="info-location-icon" />
              <text class="info-location-text">{{ stallDetail.location }}</text>
            </view>
            <view class="info-rating" v-if="stallDetail.avgRating != null && stallDetail.avgRating > 0">
              <IconSvg name="star-filled" :size="26" color="var(--color-star)" class="info-rating-icon" />
              <text class="info-rating-text">{{ stallDetail.avgRating.toFixed(1) }}</text>
            </view>
            <view class="info-desc" v-if="stallDetail.description">
              <text class="info-desc-text">{{ stallDetail.description }}</text>
            </view>
          </view>
        </CardSection>

        <!-- 全部菜品 -->
        <CardSection>
          <SectionTitle :title="`全部菜品（${dishList.length}）`" noMargin />
          <view v-if="dishList.length > 0" class="dish-list">
            <StallDishRow
              v-for="dish in dishList"
              :key="dish.id"
              :dish="dish"
              @click="goToDetail"
            />
          </view>
          <EmptyState v-else text="该档口暂无菜品" />
        </CardSection>

        <!-- 用户评价（仅展示前 3 条，点击查看全部） -->
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

        <!-- 申请关闭/纠错：不常用，降级为底部弱化的小文字链接（点击展开 Sheet） -->
        <view class="apply-link" @tap="openApply">
          <text class="apply-link-text">反馈 / 申请关闭纠错 ›</text>
        </view>
      </template>

      <!-- 加载失败 / 无数据空态 -->
      <EmptyState v-else text="档口信息加载失败" :retry="true" @retry="loadData" />
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 申请关闭/纠错 Sheet（共享组件） -->
    <ApplySheet
      :open="applyOpen"
      entity-type="STALL"
      :entity-id="stallDetail?.id || 0"
      @update:open="applyOpen = $event"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import StallDishRow from '@/components/StallDishRow.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { getStallDetail } from '@/api/canteen'
import { getReviewsByStall } from '@/api/review'
import type { StallDetail } from '@/types/canteen'
import type { Dish } from '@/types/dish'
import type { Review } from '@/types/review'

const dishStore = useDishStore()
const userStore = useUserStore()
const stallDetail = ref<StallDetail | null>(null)
const dishList = computed(() => dishStore.stallDishes)
const refresherTriggered = ref(false)
const loading = ref(true)

/** 用户评价区（前 3 条预览 + 总数，点击进全部） */
const reviewList = ref<Review[]>([])
const reviewTotal = ref(0)
const currentStallId = ref(0)

async function loadReviews() {
  if (!currentStallId.value) return
  try {
    const res = await getReviewsByStall(currentStallId.value, { sort: 'latest', isWithImage: false })
    reviewList.value = res.list
    reviewTotal.value = res.total
  } catch {
    reviewList.value = []
    reviewTotal.value = 0
  }
}

function goToReviewList() {
  uni.navigateTo({ url: `/pages/pages-detail/review-list?stallId=${currentStallId.value}` })
}

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

/** 快捷申请关闭/纠错 Sheet */
const applyOpen = ref(false)
function openApply() {
  if (!userStore.requireAuth()) return
  if (!stallDetail.value?.id) {
    uni.showToast({ title: '档口信息缺失，无法申请', icon: 'none' })
    return
  }
  applyOpen.value = true
}

async function loadData() {
  const { stallName, canteen } = dishStore.navParams
  if (!stallName || !canteen) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [detail] = await Promise.all([
      getStallDetail(canteen, stallName),
      dishStore.fetchStallDishes(canteen, stallName),
    ])
    stallDetail.value = detail
    currentStallId.value = detail.id ?? 0
    await loadReviews()
  } catch (e) {
    stallDetail.value = null
    console.error('[stall] 档口详情加载失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadData() })

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.stall-detail-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0 0; }
/* CardSection 自带 margin: var(--spacing-sm) var(--spacing-md) 提供左右 24rpx 内边距，
   不在此处重置（避免依赖 scroll-wrap 内边距，微信下更可靠），卡片不溢出 */
.info-body { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.info-location { display: flex; align-items: center; gap: var(--spacing-xs); }
.info-location-icon { font-size: 28rpx; line-height: 1; flex-shrink: 0; }
.info-location-text { font-size: var(--font-small); color: var(--text-secondary); }
.info-rating { display: flex; align-items: center; gap: var(--spacing-xs); }
.info-rating-icon { width: 26rpx; height: 26rpx; line-height: 1; flex-shrink: 0; }
.info-rating-text { font-size: var(--font-small); color: var(--text-secondary); font-weight: 600; }
.info-desc-text { font-size: var(--font-small); color: var(--text-secondary); line-height: 1.6; display: block; }
.dish-list { margin-top: var(--spacing-sm); }
.review-list { margin-top: var(--spacing-sm); }
.review-more-btn { margin-top: var(--spacing-sm); display: flex; justify-content: center; }
.review-more-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; }

/* 申请入口：不常用，降级为底部弱化的小文字链接（点击展开 Sheet） */
.apply-link { display: flex; justify-content: center; padding: var(--spacing-md) 0 var(--spacing-sm); -webkit-tap-highlight-color: transparent; }
.apply-link:active { opacity: 0.6; }
.apply-link-text { font-size: var(--font-aux); color: var(--text-tertiary); }

/* 加载骨架屏 */
.stall-skeleton { padding: var(--spacing-md); }
.sk-swiper { width: 100%; height: 360rpx; border-radius: var(--radius-card); }
.sk-info { width: 100%; height: 160rpx; border-radius: var(--radius-card); margin-top: var(--spacing-md); }
.sk-dish { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) 0; border-bottom: 2rpx solid var(--bg-page); }
.sk-dish-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-card); flex-shrink: 0; }
.sk-dish-body { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: var(--spacing-sm); }
.sk-line { width: 60%; height: 28rpx; border-radius: 6rpx; }
.sk-line-short { width: 40%; height: 24rpx; }
.skeleton { background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
