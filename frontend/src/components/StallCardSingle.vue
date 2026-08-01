<template>
  <view
    class="stall-card-single"
    :class="{ pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap="handleClick"
  >
    <!-- 档口图（圆角方图，与 StallDishRow 同尺寸 140rpx，task-13 §2.2） -->
    <view class="stall-thumb">
      <ImageFallback :src="stall.image" />
    </view>

    <!-- 信息区：名称 → 简介 → 标签 → 元信息 → 评分（顺序与 StallDishRow 对齐） -->
    <view class="stall-info">
      <text class="stall-name">{{ stall.name }}</text>
      <text v-if="stall.description" class="stall-desc">{{ stall.description }}</text>
      <view v-if="stall.tags && stall.tags.length" class="stall-tags">
        <text v-for="t in stall.tags" :key="t" class="stall-tag">{{ t }}</text>
      </view>
      <text v-if="metaText" class="stall-meta-text">{{ metaText }}</text>
      <view v-if="displayRating != null && displayRating > 0" class="star-num">
        <IconSvg name="star-filled" :size="22" color="var(--color-star)" />
        <text class="star-num-text">{{ formatRating(displayRating) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import ImageFallback from './ImageFallback.vue'
import IconSvg from './IconSvg.vue'

/**
 * StallCard 单列版（task-14 W2/W4 / task-13 §2.2）
 * 食堂详情页单列档口流，视觉语言对齐 StallDishRow（list-row，无卡片背景/圆角/阴影）。
 * 不直接显示菜品（菜品详情在档口详情页 stall.vue）。
 */
export interface StallCardItem {
  id: number
  name: string
  image?: string
  description?: string
  /** 评分 */
  rating?: number
  /** 平均星级（后端 avgRating，与 rating 同源；组件优先展示 rating，缺省回落 avgRating） */
  avgRating?: number
  /** 菜品数 */
  dishCount?: number
  /** 人均（元，展示用，已为元） */
  perCapita?: number
  /** 档口位置（楼层/窗口等） */
  location?: string
  /** 展示用元信息（如「2F · 12道菜」），由父级拼接传入 */
  meta?: string
  /** 标签（如 招牌/清真…） */
  tags?: string[]
}

const props = defineProps<{
  stall: StallCardItem
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'click', stall: StallCardItem): void
}>()

const pressed = ref(false)

/** 评分展示来源：优先 rating，缺省回落 avgRating */
const displayRating = computed(() => {
  const r = props.stall.rating ?? props.stall.avgRating
  return r != null ? Number(r) : undefined
})

const metaText = computed(() => {
  if (props.stall.meta) return props.stall.meta
  const parts: string[] = []
  if (props.stall.location) parts.push(props.stall.location)
  if (props.stall.dishCount != null) parts.push(`${props.stall.dishCount}道菜`)
  if (props.stall.perCapita != null) parts.push(`¥${props.stall.perCapita}/人`)
  return parts.join(' · ')
})

function formatRating(rating?: number): string {
  return rating != null ? rating.toFixed(1) : '0.0'
}

function handleClick() {
  emit('click', props.stall)
}
</script>

<style scoped>
/* list-row 风格：与 StallDishRow 对齐，无卡片背景/圆角/阴影，底部 2rpx 分隔线 */
.stall-card-single {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: var(--spacing-md) var(--spacing-sm);
  border-bottom: 2rpx solid var(--border-color);
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.stall-card-single.pressed { transform: scale(var(--press-scale)); }
.stall-card-single:last-child { border-bottom: none; }

.stall-thumb {
  width: 140rpx;
  height: 140rpx;
  border-radius: var(--radius-card);
  background: var(--bg-page);
  overflow: hidden;
  flex-shrink: 0;
}

.stall-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--spacing-xs);
}
.stall-name {
  font-size: var(--font-caption);
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.stall-desc {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow: hidden;
}
.stall-tags { display: flex; flex-wrap: wrap; gap: 8rpx; }
.stall-tag {
  font-size: 20rpx;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  padding: 2rpx 12rpx;
  border-radius: var(--radius-tag);
  font-weight: 600;
  flex-shrink: 0;
}
.stall-meta-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* 评分内联（与 StallDishRow .star-num 对齐） */
.star-num { display: inline-flex; align-items: center; gap: 4rpx; }
.star-num-text { font-size: 24rpx; color: var(--text-secondary); font-weight: 600; }
</style>
