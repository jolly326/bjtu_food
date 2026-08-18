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
      <text v-if="stall.topDishes && stall.topDishes.length" class="stall-topdishes">招牌：{{ stall.topDishes.join('、') }}</text>
      <text v-if="metaText" class="stall-meta-text">{{ metaText }}</text>
            <view v-if="displayRating != null && displayRating > 0" class="star-num">
              <IconSvg name="star-filled" :size="22" color="var(--color-star)" />
              <text class="star-num-text">{{ formatRating(displayRating) }}</text>
            </view>
            <text v-else class="no-rating">暂无评分</text>
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
import type { StallCardItem } from './stall-card-item'

const props = defineProps<{
  stall: StallCardItem
  loading?: boolean
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件 @click 编译为原生 bindclick，emit 参数丢失（同 MomentCard 坑）。
const emit = defineEmits<{
  (e: 'select', stall: StallCardItem): void
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
  emit('select', props.stall)
}
</script>

<style scoped>
/* 独立卡片（todo：档口卡间隔与父组件一致）：
   与 CardSection 同款 bg-card/圆角/阴影，间距由父级 WaterfallList item 的 margin 提供 */
.stall-card-single {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: transform var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.stall-card-single.pressed { transform: scale(var(--press-scale)); }

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
  gap: 6rpx;
}
.stall-name {
  font-size: var(--font-caption);
  font-weight: var(--weight-medium);
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
.stall-tags { display: flex; flex-wrap: nowrap; overflow: hidden; gap: 8rpx; max-height: 36rpx; }
.stall-tag {
  font-size: 20rpx;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  padding: 2rpx 12rpx;
  border-radius: var(--radius-tag);
  font-weight: var(--weight-semibold);
  flex-shrink: 0;
}
.stall-meta-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.stall-topdishes {
  font-size: var(--font-aux);
  color: var(--color-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.no-rating { font-size: var(--font-tiny); color: var(--text-tertiary); }

/* 评分内联（与 StallDishRow .star-num 对齐） */
.star-num { display: inline-flex; align-items: center; gap: var(--spacing-2xs); }
.star-num-text { font-size: 24rpx; color: var(--text-secondary); font-weight: var(--weight-semibold); }
</style>
