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
          <view class="canteen-hero-stats">
            <text v-if="canteenInfo.avgRating > 0" class="canteen-hero-stat">
              <IconSvg name="star" :size="22" color="#FFD166" /> {{ canteenInfo.avgRating.toFixed(1) }}
            </text>
            <text class="canteen-hero-stat">{{ canteenInfo.stallCount }} 个档口</text>
          </view>
          <text v-if="canteenInfo.location" class="canteen-hero-loc">
            <IconSvg name="location" :size="24" color="var(--text-tertiary)" /> {{ canteenInfo.location }}
          </text>
          <text v-if="canteenInfo.businessHours" class="canteen-hero-loc">
            <IconSvg name="clock" :size="24" color="var(--text-tertiary)" /> 营业 {{ canteenInfo.businessHours }}
          </text>
          <text v-if="canteenInfo.description" class="canteen-hero-desc">{{ canteenInfo.description }}</text>
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
      <SectionTitle v-if="stallList.length > 0" title="档口" />
      <view class="stall-stream" v-if="stallList.length > 0">
        <WaterfallList :list="stallList" single type="stall" @stall-click="goToStall" />
      </view>
      <EmptyState
        v-else-if="!loading"
        text="该食堂暂无档口"
        :retry="true"
        @retry="loadStalls"
      />

      <!-- 申请调整/下架：不常用，降级为底部弱化的小文字链接，不再横卡置顶 -->
      <view class="apply-link" @tap="openApply">
        <text class="apply-link-text">食堂信息有误？申请调整 / 下架 ›</text>
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 申请调整/下架 Sheet（task-12.1） -->
    <view v-if="applyOpen" class="sheet-mask" @tap="applyOpen = false" />
    <view class="apply-sheet" :class="{ open: applyOpen }">
      <view class="sheet-head">
        <text class="sheet-title">申请调整 / 下架</text>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @click="applyOpen = false" />
      </view>
      <view class="form-block">
        <text class="form-label">申请动作</text>
        <view class="seg-row">
          <view class="seg" :class="{ on: applyAction === 'CHANGE' }" @tap="applyAction = 'CHANGE'">调整 / 变更</view>
          <view class="seg" :class="{ on: applyAction === 'CLOSE' }" @tap="applyAction = 'CLOSE'">下架</view>
        </view>
      </view>
      <view class="form-block">
        <text class="form-label">说明（选填）</text>
        <textarea class="form-textarea" v-model="applyReason" placeholder="请描述调整/下架原因…" maxlength="500" :auto-height="true" />
      </view>
      <view class="sheet-submit">
        <AppButton text="提交申请" :loading="applySubmitting" @click="submitCanteenApply" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import IconSvg from '@/components/IconSvg.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import type { StallCardItem } from '@/components/StallCardSingle.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppButton from '@/components/AppButton.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { getCanteensWithStalls, getCanteenImages } from '@/api/canteen'
import { submitApply } from '@/api/apply'
import { getImageUrl } from '@/utils/image'

const dishStore = useDishStore()
const userStore = useUserStore()
const canteenName = ref('')
const canteenId = ref(0)
/** 重构后的单列档口卡数据（StallCardItem[]） */
const stallList = ref<StallCardItem[]>([])
const loading = ref(false)
const refreshing = ref(false)

/** 食堂介绍区块信息（task-13 §2.2 补充营业时间/地址/档口数/综合评分等） */
const canteenInfo = ref<{
  name: string
  image: string
  location: string
  description: string
  /** 营业时间（后端可选返回，缺省则不展示） */
  businessHours: string
  /** 档口数 */
  stallCount: number
  /** 综合评分（档口均分派生，后端返整体评分优先） */
  avgRating: number
} | null>(null)

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
    // 综合评分：优先后端整体评分，否则由各档口评分均值派生
    const ratedStalls = stalls.filter((s: any) => (s.avgRating ?? s.rating))
    const avgRating = Number(current?.avgRating ?? 0) ||
      (ratedStalls.length
        ? ratedStalls.reduce((sum: number, s: any) => sum + Number(s.avgRating ?? s.rating ?? 0), 0) / ratedStalls.length
        : 0)
    // 食堂介绍区块（task-13 §2.2：补充营业时间/地址/档口数/综合评分等）
    canteenInfo.value = {
      name: current?.name || canteenName.value,
      image: (imgMap as Record<string, string>)[canteenName.value] || firstImage(current?.images),
      location: current?.location || '',
      description: current?.description || '',
      businessHours: current?.businessHours || '',
      stallCount: stalls.length,
      avgRating,
    }
    // 单列档口卡：图 + 名 + 简介 + 评分/菜品数/人均/标签（task-13 §2.2，卡片尺寸不变）
    stallList.value = stalls.map((stall: any) => ({
      id: Number(stall.id || 0),
      name: stall.name || '',
      image: firstImage(stall.images),
      description: stall.description || '',
      rating: stall.avgRating ?? stall.rating ?? 0,
      dishCount: Number(stall.dishCount ?? 0),
      perCapita: stall.perCapita != null ? Number(stall.perCapita) : undefined,
      location: stall.location || current?.location || '',
      tags: Array.isArray(stall.tags) ? stall.tags : (String(stall.tags || '').split(',').map((t: string) => t.trim()).filter(Boolean)),
    }))
  } catch {
    stallList.value = []
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

/** 快捷申请调整/下架（task-12.1，POST /my/apply，CLOSE/CHANGE + entityId=当前食堂） */
const applyOpen = ref(false)
const applyAction = ref<'CLOSE' | 'CHANGE'>('CHANGE')
const applyReason = ref('')
const applySubmitting = ref(false)

function openApply() {
  if (!userStore.requireAuth()) return
  if (!canteenId.value) {
    uni.showToast({ title: '食堂信息缺失，无法申请', icon: 'none' })
    return
  }
  applyAction.value = 'CHANGE'
  applyReason.value = ''
  applyOpen.value = true
}

async function submitCanteenApply() {
  if (!canteenId.value) return
  applySubmitting.value = true
  try {
    await submitApply({
      entityType: 'CANTEEN',
      applyType: applyAction.value,
      entityId: canteenId.value,
      payload: { reason: applyReason.value.trim() },
    })
    uni.showToast({ title: '申请已提交', icon: 'success' })
    applyOpen.value = false
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    applySubmitting.value = false
  }
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
}

/* ① 食堂介绍与信息区块 */
.canteen-hero {
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  margin: var(--spacing-md);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
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
.canteen-hero-loc { display: inline-flex; align-items: center; gap: 4rpx; font-size: var(--font-aux); color: var(--text-secondary); }
.canteen-hero-desc { font-size: var(--font-aux); color: var(--text-secondary); line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; }
.canteen-hero-stats { display: flex; flex-wrap: wrap; align-items: center; gap: var(--spacing-md); margin-top: var(--spacing-xs); }
.canteen-hero-stat { display: inline-flex; align-items: center; gap: 4rpx; font-size: var(--font-aux); color: var(--text-tertiary); font-weight: 600; }

/* ② 档口单列流 */
.stall-stream { padding: 0 var(--spacing-md); box-sizing: border-box; }

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

/* 申请 Sheet（task-12.1，保留底部弹层表单） */
.apply-sheet { position: fixed; left: 0; right: 0; bottom: 0; background: var(--bg-card); border-radius: var(--radius-modal) var(--radius-modal) 0 0; box-shadow: var(--shadow-modal); z-index: 100; transform: translateY(100%); transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }
.apply-sheet.open { transform: translateY(0); }
.form-block { padding: var(--spacing-md) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); }
.form-label { display: block; font-size: var(--font-aux); font-weight: 700; color: var(--text-secondary); margin-bottom: var(--spacing-sm); }
.seg-row { display: flex; gap: var(--spacing-sm); }
.seg { padding: var(--spacing-xs) var(--spacing-lg); border-radius: var(--radius-tag); background: var(--bg-soft); font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; transition: background 0.15s, transform 0.12s; -webkit-tap-highlight-color: transparent; }
.seg:active { transform: scale(0.97); }
.seg.on { background: var(--color-primary); color: var(--text-white); }
.form-textarea { width: 100%; min-height: 160rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: var(--spacing-sm) var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.sheet-submit { padding: var(--spacing-md) var(--spacing-lg); }

@media (prefers-reduced-motion: reduce) {
  .apply-sheet { transition: opacity 0.2s ease; }
}

</style>
