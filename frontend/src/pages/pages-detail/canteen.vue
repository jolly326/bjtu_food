<template>
  <view class="page stall-page">
    <Header :title="canteenName || '食堂详情'" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <!-- ① 食堂介绍与信息区块（图 + 名称 + 简介 + 基础信息，task-13 §2.2 内容更详细） -->
      <view class="canteen-hero" v-if="canteenInfo">
        <view class="canteen-hero-img">
          <image v-if="canteenInfo.image" :src="canteenInfo.image" mode="aspectFill" class="canteen-hero-img-el" />
          <view v-else class="canteen-hero-ph">
            <IconSvg name="dish" :size="96" color="var(--text-tertiary)" />
          </view>
        </view>
        <view class="canteen-hero-info">
          <text class="canteen-hero-name">{{ canteenInfo.name }}</text>

          <!-- 有序信息区：仅位置 + 介绍（评分/档口数/营业时段已移出 Hero，档口数见列表标题） -->
          <view class="canteen-info">
            <view class="canteen-info-row" v-if="canteenInfo.location">
              <IconSvg name="location" :size="28" color="var(--text-tertiary)" class="canteen-info-icon" />
              <text class="canteen-info-label">位置</text>
              <text class="canteen-info-value">{{ canteenInfo.location }}</text>
            </view>
            <text v-if="canteenInfo.description" class="canteen-info-desc">{{ canteenInfo.description }}</text>
          </view>
        </view>
      </view>
      <view v-else-if="loading" class="canteen-hero canteen-hero-skeleton">
        <view class="canteen-hero-img skeleton-block" />
        <view class="canteen-hero-info">
          <view class="skeleton-line skeleton-name" />
          <view class="skeleton-line skeleton-loc" />
        </view>
      </view>

      <!-- ② 各档口单列卡片流（不直接显示菜品，与档口详情同构） -->
      <view class="stall-section" v-if="stallList.length > 0">
        <SectionTitle :title="`档口列表（${stallList.length}）`" />
        <view class="stall-stream">
          <WaterfallList :list="stallList" single type="stall" @stall-click="goToStall" />
        </view>
      </view>
      <EmptyState
        v-else-if="!loading"
        text="该食堂暂无档口"
        :retry="true"
        @retry="loadStalls"
      />

      <!-- 用户评价（仅展示前 3 条，点击查看全部） -->
      <view class="review-section" v-if="reviewTotal > 0 || reviewList.length > 0">
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
      </view>

      <!-- 申请调整/下架：不常用，降级为底部弱化的小文字链接，不再横卡置顶 -->
      <view class="apply-link" @tap="openApply">
        <text class="apply-link-text">食堂信息有误？申请调整 / 下架 ›</text>
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 申请调整/下架 Sheet（共享组件） -->
    <ApplySheet
      :open="applyOpen"
      entity-type="CANTEEN"
      :entity-id="canteenId"
      @update:open="applyOpen = $event"
    />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import IconSvg from '@/components/IconSvg.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import CardSection from '@/components/CardSection.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import type { StallCardItem } from '@/components/StallCardSingle.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { getCanteensWithStalls, getCanteenImages } from '@/api/canteen'
import { getReviewsByCanteen } from '@/api/review'
import type { Review } from '@/types/review'

const dishStore = useDishStore()
const userStore = useUserStore()
const canteenName = ref('')
const canteenId = ref(0)
/** 重构后的单列档口卡数据（StallCardItem[]） */
const stallList = ref<StallCardItem[]>([])
const loading = ref(false)
const refreshing = ref(false)

/** 食堂介绍区块信息（Hero 仅含名称 + 位置 + 介绍 + 缩略图，评分/档口数/营业时段已移出） */
const canteenInfo = ref<{
  name: string
  image: string
  location: string
  description: string
} | null>(null)

/** 用户评价区（前 3 条预览 + 总数，点击进全部） */
const reviewList = ref<Review[]>([])
const reviewTotal = ref(0)

async function loadReviews() {
  if (!canteenId.value) return
  try {
    const res = await getReviewsByCanteen(canteenId.value, { sort: 'latest', isWithImage: false })
    reviewList.value = res.list
    reviewTotal.value = res.total
  } catch {
    reviewList.value = []
    reviewTotal.value = 0
  }
}

function goToReviewList() {
  uni.navigateTo({ url: `/pages/pages-detail/review-list?canteenId=${canteenId.value}` })
}

function firstImage(value: unknown): string {
  if (Array.isArray(value)) return (value.find(item => typeof item === 'string') || '') as string
  if (typeof value === 'string') return value
  return ''
}

async function loadStalls() {
  if (!canteenName.value) return
  loading.value = true
  try {
    const [canteens, imgMap] = await Promise.all([
      getCanteensWithStalls(),
      getCanteenImages().catch(() => ({} as Record<string, string>)),
    ])
    const current = (canteens as any[]).find((item: any) => item.name === canteenName.value)
    canteenId.value = Number(current?.id || 0)
    const stalls = (current?.stalls || []) as any[]
    // 食堂介绍区块（仅名称 + 位置 + 介绍；评分/档口数见列表标题，已移除冗余字段）
    canteenInfo.value = {
      name: current?.name || canteenName.value,
      image: (imgMap as Record<string, string>)[canteenName.value] || firstImage(current?.images),
      location: current?.location || '',
      description: current?.description || '',
    }
    // 单列档口卡：图 + 名 + 简介 + 评分(avgRating)/菜品数/人均/标签
    stallList.value = stalls.map((stall: any) => ({
      id: Number(stall.id || 0),
      name: stall.name || '',
      image: firstImage(stall.images),
      description: stall.description || '',
      rating: stall.avgRating ?? stall.rating ?? 0,
      avgRating: stall.avgRating != null ? Number(stall.avgRating) : (stall.rating != null ? Number(stall.rating) : undefined),
      dishCount: Number(stall.dishCount ?? 0),
      perCapita: stall.perCapita != null ? Number(stall.perCapita) : undefined,
      location: stall.location || current?.location || '',
      tags: Array.isArray(stall.tags) ? stall.tags : (String(stall.tags || '').split(',').map((t: string) => t.trim()).filter(Boolean)),
    }))
    await loadReviews()
  } catch {
    stallList.value = []
    reviewList.value = []
    reviewTotal.value = 0
    canteenInfo.value = { name: canteenName.value, image: '', location: '', description: '' }
  } finally {
    loading.value = false
  }
}

function goToStall(stall: StallCardItem) {
  dishStore.navParams.stallName = stall.name
  dishStore.navParams.canteen = canteenName.value
  uni.navigateTo({ url: '/pages/pages-detail/stall' })
}

/** 快捷申请调整/下架（共享 ApplySheet 处理 CLOSE/CHANGE + entityId=当前食堂） */
const applyOpen = ref(false)

function openApply() {
  if (!userStore.requireAuth()) return
  if (!canteenId.value) {
    uni.showToast({ title: '食堂信息缺失，无法申请', icon: 'none' })
    return
  }
  applyOpen.value = true
}

function onRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  loadStalls().finally(() => { refreshing.value = false })
}

onLoad(async (query) => {
  if (query?.canteen) {
    canteenName.value = decodeURIComponent(query.canteen as string)
  }
  await loadStalls()
})
</script>

<style scoped>
.stall-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
}
.scroll-wrap {
  flex: 1;
  overflow-y: auto;
  /* 仅保留顶部内边距，左右内边距下放到各区块，避免微信 scroll-view 内边距不稳导致卡片溢出 */
  padding: var(--spacing-md) 0 0;
}

/* ① 食堂介绍与信息区块 */
.canteen-hero {
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  margin: 0 var(--spacing-md) var(--spacing-md);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
  width: auto;
}
.canteen-hero-img {
  width: 200rpx;
  height: 200rpx;
  border-radius: var(--radius-card);
  background: var(--bg-page);
  overflow: hidden;
  flex-shrink: 0;
}
.canteen-hero-img-el { width: 100%; height: 100%; }
.canteen-hero-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.canteen-hero-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.canteen-hero-name { font-size: var(--font-h3); font-weight: 800; color: var(--text-primary); letter-spacing: -0.01em; }
/* 有序信息区：图标 + 标签 + 值行 */
.canteen-info { display: flex; flex-direction: column; gap: var(--spacing-sm); margin-top: var(--spacing-xs); padding-top: var(--spacing-xs); border-top: 2rpx solid var(--border-color); }
.canteen-info-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.canteen-info-icon { width: 28rpx; height: 28rpx; line-height: 1; flex-shrink: 0; }
.canteen-info-label { flex-shrink: 0; font-size: var(--font-aux); color: var(--text-tertiary); font-weight: 600; }
.canteen-info-value { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); font-weight: 500; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.canteen-info-desc { font-size: var(--font-aux); color: var(--text-secondary); line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; }

/* ② 档口单列流：左右 24rpx 内边距由 .stall-section 提供，确保卡片不溢出屏幕右侧 */
.stall-section {
  padding: var(--spacing-md) var(--spacing-md) 0;
  box-sizing: border-box;
  width: 100%;
}
.stall-stream { margin: 0; box-sizing: border-box; width: 100%; }
.review-section { padding: 0; box-sizing: border-box; width: 100%; }
.review-list { margin-top: var(--spacing-sm); }
.review-more-btn { margin-top: var(--spacing-sm); display: flex; justify-content: center; }
.review-more-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; }

/* hero 骨架屏 */
.canteen-hero-skeleton { }
.skeleton-block { background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-line { border-radius: 6rpx; background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-name { width: 55%; height: 36rpx; }
.skeleton-loc { width: 60%; height: 24rpx; margin-top: var(--spacing-sm); }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* 申请入口：不常用，降级为底部弱化的小文字链接（不再横卡置顶） */
.apply-link {
  display: flex;
  justify-content: center;
  padding: var(--spacing-md) 0 var(--spacing-sm);
  -webkit-tap-highlight-color: transparent;
}
.apply-link:active { opacity: 0.6; }
.apply-link-text { font-size: var(--font-aux); color: var(--text-tertiary); }

</style>
