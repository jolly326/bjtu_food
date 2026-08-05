<template>
  <view class="page stall-page" :class="{ 'theme-dark': theme.isDark }">
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

        <!-- 食堂信息 hero（无"食堂信息"大标题：直接名称 + 位置 + 介绍 + "信息有误？"弱入口） -->
        <CardSection>
          <view class="info-body">
            <view class="info-head">
              <text class="info-name">{{ canteenInfo.name }}</text>
              <text class="feedback-link" @tap="openApply">信息有误？</text>
            </view>
            <view class="info-location">
              <IconSvg name="location" :size="24" color="var(--color-primary)" class="info-location-icon" />
              <text class="info-location-text">{{ canteenInfo.location }}</text>
            </view>
            <view class="info-desc" v-if="canteenInfo.description">
              <text class="info-desc-text">{{ canteenInfo.description }}</text>
            </view>
          </view>
        </CardSection>

        <!-- ② 各档口单列卡片流（无"档口列表"标题，直接一列独立卡片：左图右信息） -->
        <WaterfallList v-if="stallList.length > 0" class="stall-waterfall" :list="stallList" single type="stall" @stall-click="goToStall" />
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
import { useThemeStore } from '@/stores/theme'
const theme = useThemeStore()
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import IconSvg from '@/components/IconSvg.vue'
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
      topDishes: Array.isArray(stall.topDishes) ? stall.topDishes : [],
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

/* 食堂信息 hero（无分区大标题：名称 + 位置 + 介绍 + "信息有误？"弱入口） */
.info-body { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.info-head {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: var(--spacing-sm);
}
.info-name {
  font-size: var(--font-h1);
  font-weight: var(--weight-heavy);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h1);
  line-height: 1.3;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 位置：图标 + 文本独立一行（与名称不同行） */
.info-location {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
}
.info-location-icon { font-size: 24rpx; line-height: 1; flex-shrink: 0; }
.info-location-text { font-size: var(--font-small); font-weight: var(--weight-medium); color: var(--text-secondary); }
.info-desc-text {
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

/* ② 档口单列流：每张独立卡片（间距由 WaterfallList item margin 提供，与 CardSection 一致） */

/* 反馈入口：不常用，弱化在标题行右侧的小文字链接（点击展开 Sheet） */
.feedback-link {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
  /* 扩大命中区（负 margin 保持视觉位置），按压反馈对齐档口详情页 */
  padding: var(--spacing-sm);
  margin: calc(var(--spacing-sm) * -1);
  border-radius: var(--radius-tag);
  transition: opacity 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.feedback-link:active { opacity: 0.55; }

/* 加载骨架屏 */
.canteen-skeleton { padding: var(--spacing-md); }
.sk-swiper { width: 100%; height: 400rpx; border-radius: var(--radius-card); }
.sk-info { width: 100%; height: 160rpx; border-radius: var(--radius-card); margin-top: var(--spacing-md); }
.sk-stall { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) 0; border-bottom: 2rpx solid var(--border-color); }
.sk-stall-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-card); flex-shrink: 0; }
.sk-stall-body { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: var(--spacing-sm); }
.sk-line { width: 60%; height: 28rpx; border-radius: 6rpx; }
.sk-line-short { width: 40%; height: 24rpx; }
/* 骨架屏：复用全局 shimmer（App.vue 1.4s），统一全站加载节奏 */
.skeleton { background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%); background-size: 200% 100%; animation: shimmer 1.4s ease infinite; }
</style>
