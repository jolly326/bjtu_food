<template>
  <view class="page stall-page">
    <Header :title="canteenName || '食堂详情'" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <!-- 加载骨架 -->
      <view v-if="loading" class="canteen-skeleton">
        <view class="sk-swiper skeleton" />
        <view class="sk-info skeleton" />
        <view class="sk-stall" v-for="s in 3" :key="s">
          <view class="sk-stall-img skeleton" />
          <view class="sk-stall-body">
            <view class="sk-line skeleton" />
            <view class="sk-line sk-line-short skeleton" />
          </view>
        </view>
      </view>

      <template v-else-if="canteenInfo">
        <ImageSwiper :images="canteenInfo.images" />

        <!-- 食堂信息（合并卡片，含位置与简介，无评分） -->
        <CardSection>
          <SectionTitle title="食堂信息" noMargin>
            <template #extra>
              <text class="feedback-link" @tap="openApply">反馈信息有误</text>
            </template>
          </SectionTitle>
          <view class="info-body">
            <text class="info-name">{{ canteenInfo.name }}</text>
            <view class="info-location" v-if="canteenInfo.location">
              <IconSvg name="location" :size="26" color="var(--text-tertiary)" class="info-location-icon" />
              <text class="info-location-text">{{ canteenInfo.location }}</text>
            </view>
            <view class="info-desc" v-if="canteenInfo.description">
              <text class="info-desc-text">{{ canteenInfo.description }}</text>
            </view>
          </view>
        </CardSection>

        <!-- ② 各档口单列卡片流（标题 + 卡片整体包在一张卡片里） -->
        <CardSection v-if="stallList.length > 0">
          <SectionTitle :title="`档口列表（${stallList.length}）`" noMargin />
          <WaterfallList class="stall-waterfall" :list="stallList" single type="stall" @stall-click="goToStall" />
        </CardSection>
        <EmptyState
          v-else
          text="该食堂暂无档口"
          :retry="true"
          @retry="loadStalls"
        />

        <!-- 用户评价（仅展示前 3 条，点击查看全部） -->
        <CardSection v-if="reviewTotal > 0 || reviewList.length > 0">
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
      </template>

      <!-- 加载失败 / 无数据空态 -->
      <EmptyState v-else text="食堂信息加载失败" :retry="true" @retry="loadStalls" />
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
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import IconSvg from '@/components/IconSvg.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import type { StallCardItem } from '@/components/StallCardSingle.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { getCanteensWithStalls, normalizeImages } from '@/api/canteen'
import { getReviewsByCanteen } from '@/api/review'
import type { Review } from '@/types/review'

const dishStore = useDishStore()
const userStore = useUserStore()
const canteenName = ref('')
const canteenId = ref(0)
/** 重构后的单列档口卡数据（StallCardItem[]） */
const stallList = ref<StallCardItem[]>([])
const loading = ref(true)
const refreshing = ref(false)

/** 食堂介绍区块信息（images + 位置 + 介绍；无评分） */
const canteenInfo = ref<{
  name: string
  images: string[]
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

async function loadStalls() {
  if (!canteenName.value) return
  loading.value = true
  try {
    const canteens = await getCanteensWithStalls()
    const current = (canteens as any[]).find((item: any) => item.name === canteenName.value)
    canteenId.value = Number(current?.id || 0)
    const stalls = (current?.stalls || []) as any[]
    // 食堂介绍区块（images + 位置 + 介绍；评分已移除）
    canteenInfo.value = {
      name: current?.name || canteenName.value,
      images: normalizeImages(current?.images),
      location: current?.location || '',
      description: current?.description || '',
    }
    // 单列档口卡：图 + 名 + 简介 + 评分(avgRating)/菜品数/人均/标签
    stallList.value = stalls.map((stall: any) => ({
      id: Number(stall.id || 0),
      name: stall.name || '',
      image: (Array.isArray(stall.images) ? (stall.images.find((i: unknown) => typeof i === 'string') || '') : '') as string,
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
    canteenInfo.value = { name: canteenName.value, images: [], location: '', description: '' }
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

/* 食堂信息卡片（合并卡片，含位置与简介，无评分） */
.info-body { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.info-location { display: flex; align-items: center; gap: var(--spacing-xs); }
.info-location-icon { font-size: 28rpx; line-height: 1; flex-shrink: 0; }
.info-name { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); line-height: 1.3; }
.info-location-text { font-size: var(--font-caption); color: var(--text-secondary); font-weight: 500; }
.info-desc-text { font-size: var(--font-caption); color: var(--text-secondary); line-height: 1.4; display: block; }

/* ② 档口单列流：标题 + 卡片整体包在 CardSection 内，间距由卡片自身提供 */
.stall-waterfall { margin-top: var(--spacing-sm); }
.review-list { margin-top: var(--spacing-sm); }
.review-more-btn { margin-top: var(--spacing-sm); display: flex; justify-content: center; }
.review-more-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; }

/* 反馈入口：不常用，弱化在标题行右侧的小文字链接（点击展开 Sheet） */
.feedback-link {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
}

/* 加载骨架屏 */
.canteen-skeleton { padding: var(--spacing-md); }
.sk-swiper { width: 100%; height: 400rpx; border-radius: var(--radius-card); }
.sk-info { width: 100%; height: 160rpx; border-radius: var(--radius-card); margin-top: var(--spacing-md); }
.sk-stall { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) 0; border-bottom: 2rpx solid var(--bg-page); }
.sk-stall-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-card); flex-shrink: 0; }
.sk-stall-body { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: var(--spacing-sm); }
.sk-line { width: 60%; height: 28rpx; border-radius: 6rpx; }
.sk-line-short { width: 40%; height: 24rpx; }
.skeleton { background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
