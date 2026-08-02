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
            <view class="info-head">
              <text class="info-name">{{ canteenInfo.name }}</text>
              <view class="info-location">
                <IconSvg name="location" :size="26" color="var(--color-primary)" class="info-location-icon" />
                <text class="info-location-text">{{ canteenInfo.location }}</text>
              </view>
            </view>
            <!-- 评分/人均：前端由档口聚合占位，待后端字段 -->
            <view class="info-stats">
              <text class="info-stat">评分 <text class="info-stat-num">{{ canteenRating || '—' }}</text> · 人均 ¥<text class="info-stat-num">{{ canteenPerCapita || '—' }}</text></text>
              <text class="info-stat-note">（来自档口聚合，待后端字段）</text>
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
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import IconSvg from '@/components/IconSvg.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import type { StallCardItem } from '@/components/StallCardSingle.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { getCanteensWithStalls, normalizeImages } from '@/api/canteen'

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
  } catch {
    stallList.value = []
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

/**
 * 食堂级 评分/人均 占位（前端聚合，待后端字段）。
 * 用户已批准在食堂详情页展示，但 API 暂无 canteen-level avgRating/perCapita，
 * 故先由各档口的 avgRating/perCapita 取均值占位。
 */
const canteenRating = computed(() => {
  const ratings = stallList.value
    .map((s) => s.avgRating)
    .filter((r): r is number => typeof r === 'number')
  if (ratings.length === 0) return 0
  const mean = ratings.reduce((a, b) => a + b, 0) / ratings.length
  return Math.round(mean * 10) / 10
})
const canteenPerCapita = computed(() => {
  const caps = stallList.value
    .map((s) => s.perCapita)
    .filter((p): p is number => typeof p === 'number')
  if (caps.length === 0) return 0
  const mean = caps.reduce((a, b) => a + b, 0) / caps.length
  return Math.round(mean)
})

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
.info-head {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: var(--spacing-sm);
}
.info-name {
  font-size: var(--font-h3);
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.3;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.info-location {
  display: flex;
  align-items: center;
  gap: 4rpx;
  flex-shrink: 0;
}
.info-location-icon { font-size: 28rpx; line-height: 1; flex-shrink: 0; }
.info-location-text { font-size: var(--font-caption); font-weight: 600; color: var(--text-secondary); }
/* 评分/人均占位行（前端聚合，待后端字段） */
.info-stats { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8rpx; }
.info-stat { font-size: var(--font-aux); color: var(--text-secondary); }
.info-stat-num { font-variant-numeric: tabular-nums; font-weight: 600; color: var(--text-primary); }
.info-stat-note { font-size: var(--font-tiny); color: var(--text-tertiary); }
.info-desc-text {
  font-size: var(--font-caption);
  font-weight: 400;
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

/* ② 档口单列流：标题 + 卡片整体包在 CardSection 内，间距由卡片自身提供 */
.stall-waterfall { margin-top: var(--spacing-sm); }

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
.sk-stall { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) 0; border-bottom: 2rpx solid var(--border-color); }
.sk-stall-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-card); flex-shrink: 0; }
.sk-stall-body { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: var(--spacing-sm); }
.sk-line { width: 60%; height: 28rpx; border-radius: 6rpx; }
.sk-line-short { width: 40%; height: 24rpx; }
.skeleton { background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
