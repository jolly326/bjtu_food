<script setup lang="ts">
/**
 * 内联展开卡（docs/ui/client/公共组件与形态基线.md §2.2 `DishExpandCard`）。
 *
 * 不遮罩、不弹窗：直接占据网格中一整行，网格其余部分仍可滚动 / 点选。
 * 进入用 `scale(.96)` + 淡入（`--duration-base` + `--ease-out`），无过冲。
 */
import { computed } from 'vue'

import type { DishLocationVO, DishVO } from '@/api'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import { IMAGE } from '@/constants'
import { useImageSource } from '@/hooks'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{ dish: DishVO }>()

const emit = defineEmits<{
  (e: 'collapse'): void
  (e: 'feedback'): void
}>()

const { src, onError } = useImageSource(() => props.dish.imageUrl, IMAGE.CARD_THUMB_WIDTH)

const priceFrom = computed(() => {
  const prices = props.dish.locations
    .map((loc) => loc.price)
    .filter((price): price is number => price !== null)
  return prices.length > 0 ? Math.min(...prices) : null
})

function priceText(price: number | null): string {
  return price === null ? '价格待定' : `¥${(price / 100).toFixed(2)}`
}

function placeText(loc: DishLocationVO): string {
  return `${loc.canteenName} · ${loc.floor || '暂无'} · ${loc.stallName || '暂无'}`
}
</script>

<template>
  <view class="card" :aria-label="`${dish.name} 详情`">
    <view class="card__cover">
      <image v-if="src" class="card__img" :src="src" mode="aspectFill" @error="onError" />
      <view v-else class="card__ph"><ImagePlaceholder /></view>
    </view>

    <view class="card__head">
      <text class="card__name">{{ dish.name }}</text>
      <view class="card__collapse" hover-class="card__collapse--press" @tap="emit('collapse')">
        收起
      </view>
    </view>

    <view class="card__meta">
      <text class="card__metaText">{{ dish.locations.length }} 处售卖</text>
      <text v-if="priceFrom !== null" class="card__metaPrice">
        ¥{{ (priceFrom / 100).toFixed(2) }} 起
      </text>
    </view>

    <text class="card__desc">{{ dish.description || '暂无描述' }}</text>

    <view class="card__locations">
      <view v-for="(loc, index) in dish.locations" :key="index" class="card__loc">
        <text class="card__locText">{{ placeText(loc) }}</text>
        <text class="card__locPrice">{{ priceText(loc.price) }}</text>
      </view>
      <text v-if="dish.locations.length === 0" class="card__locText card__locText--muted">
        暂无售卖点
      </text>
    </view>

    <view class="card__feedback" hover-class="card__feedback--press" @tap="emit('feedback')">
      <text class="card__feedbackText">反馈这道菜</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  box-sizing: border-box;
  width: 100%;
  padding: var(--spacing-lg);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-modal);
  background: var(--bg-card);
  box-shadow: var(--shadow-soft);
  transform-origin: top center;
  will-change: transform, opacity;
  animation: card-in var(--duration-base) var(--ease-out);
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: scale(0.96);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.card__cover {
  position: relative;
  height: 0;
  padding-top: 56.25%;
  overflow: hidden;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
  background: var(--bg-placeholder);
}

.card__img,
.card__ph {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.card__name {
  font-size: var(--font-title);
  color: var(--text-title);
}

.card__collapse {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-sizing: border-box;
  height: var(--control-height-sm);
  padding: 0 var(--spacing-md);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--pill-bg);
  font-size: var(--font-subtitle);
  color: var(--text-title);
}

/* 行内小胶囊视觉高度小于触达下限，命中区纵向补足到 --tap-target-size */
.card__collapse::after {
  content: '';
  position: absolute;
  top: calc((var(--tap-target-size) - var(--control-height-sm)) / -2);
  right: 0;
  left: 0;
  height: var(--tap-target-size);
}

.card__collapse--press {
  opacity: 0.7;
}

.card__meta {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
}

.card__metaText {
  font-size: var(--font-subtitle);
  color: var(--text-body);
}

.card__metaPrice {
  font-size: var(--font-body);
  color: var(--text-title);
}

.card__desc {
  font-size: var(--font-body);
  color: var(--text-body);
}

.card__locations {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding-top: var(--spacing-sm);
  border-top: var(--border-width) solid var(--sheet-divider);
}

.card__loc {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.card__locText {
  min-width: 0;
  font-size: var(--font-subtitle);
  color: var(--text-body);
}

.card__locPrice {
  flex-shrink: 0;
  font-size: var(--font-subtitle);
  color: var(--text-title);
}

.card__locText--muted {
  color: var(--text-disabled);
}

.card__feedback {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: var(--control-height);
  padding: 0 var(--spacing-lg);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--pill-bg);
  font-size: var(--font-body);
  color: var(--text-title);
}

.card__feedback--press {
  opacity: 0.7;
}
</style>
