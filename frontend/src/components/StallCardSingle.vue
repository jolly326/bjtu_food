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
    <!-- 档口图（圆角方图，task-13 §2.2） -->
    <view class="stall-thumb">
      <image v-if="stall.image" :src="stall.image" mode="aspectFill" class="stall-thumb-img" />
      <view v-else class="stall-thumb-ph">
        <IconSvg name="dish" :size="56" color="var(--text-tertiary)" />
      </view>
      <view v-if="stall.rating != null" class="stall-rating-badge">
        <IconSvg name="star" :size="20" color="#FFD166" />
        <text class="stall-rating-value">{{ formatRating(stall.rating) }}</text>
      </view>
    </view>

    <!-- 信息区：名称 + 简介 + 评分/菜品数/人均 + 标签 -->
    <view class="stall-info">
      <text class="stall-name">{{ stall.name }}</text>
      <text v-if="stall.description" class="stall-desc">{{ stall.description }}</text>
      <view v-if="metaText" class="stall-meta">
        <text class="stall-meta-text">{{ metaText }}</text>
      </view>
      <view v-if="stall.tags && stall.tags.length" class="stall-tags">
        <text v-for="t in stall.tags" :key="t" class="stall-tag">{{ t }}</text>
      </view>
    </view>

    <view class="stall-go">
      <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import IconSvg from './IconSvg.vue'

/**
 * StallCard 单列版（task-14 W2/W4 / task-13 §2.2）
 * 用于食堂详情页的单列档口流：档口图 + 名称 + 简介 + 评分/标签，
 * 不直接显示菜品（菜品详情在档口详情页 stall.vue）。
 */
export interface StallCardItem {
  id: number
  name: string
  image?: string
  description?: string
  /** 评分（0 视为新，展示「新」） */
  rating?: number
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

const metaText = computed(() => {
  if (props.stall.meta) return props.stall.meta
  const parts: string[] = []
  if (props.stall.location) parts.push(props.stall.location)
  if (props.stall.dishCount != null) parts.push(`${props.stall.dishCount}道菜`)
  if (props.stall.perCapita != null) parts.push(`人均¥${props.stall.perCapita}`)
  return parts.join(' · ')
})

function formatRating(rating?: number): string {
  if (rating == null || rating === 0) return '新'
  return rating.toFixed(1)
}

function handleClick() {
  emit('click', props.stall)
}
</script>

<style scoped>
.stall-card-single {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--spacing-sm) var(--spacing-md);
  box-shadow: var(--shadow-card);
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.stall-card-single.pressed { transform: scale(0.97); }

.stall-thumb {
  position: relative;
  width: 140rpx;
  height: 140rpx;
  border-radius: var(--radius-card);
  background: var(--bg-page);
  overflow: hidden;
  flex-shrink: 0;
}
.stall-thumb-img { width: 100%; height: 100%; }
.stall-thumb-ph {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
}
.stall-rating-badge {
  position: absolute;
  left: 8rpx; bottom: 8rpx;
  display: inline-flex; align-items: center; gap: 4rpx;
  background: var(--overlay-dark-strong);
  border-radius: var(--radius-card);
  padding: 2rpx 8rpx;
}
.stall-rating-value {
  color: var(--text-white);
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1;
}

.stall-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}
.stall-name {
  font-size: var(--font-subtitle);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.stall-desc {
  font-size: var(--font-aux);
  color: var(--text-secondary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
.stall-meta { display: flex; align-items: center; }
.stall-meta-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
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

.stall-go {
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
</style>
