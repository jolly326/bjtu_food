<script setup lang="ts">
/**
 * 结果卡（docs/ui/client/菜品首页.md `DishResultCard`）。
 *
 * 一卡 = 一道菜的一个在售位置：16:9 封面 + 单列三段信息区。
 * 头部（菜名 / 售价）→ 描述（最多 3 行，超长「展开」）→ 底行（左「反馈」、右位置）。
 */
import { computed } from 'vue'

import type { DishLocationVO, DishVO } from '@/api'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import { IMAGE, RESULT } from '@/constants'
import { useImageSource } from '@/hooks'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  dish: DishVO
  loc: DishLocationVO
  expanded: boolean
}>()

const emit = defineEmits<{
  (e: 'open', locationId: number): void
  (e: 'feedback', dishId: number): void
}>()

const { src, onError } = useImageSource(() => props.dish.imageUrl, IMAGE.RESULT_THUMB_WIDTH)

const needFull = computed(
  () => props.dish.description.length > RESULT.DESCRIPTION_PREVIEW_CHARS,
)
const priceText = computed(() =>
  props.dish.price === null ? '价格待定' : `¥${(props.dish.price / 100).toFixed(2)}`,
)
const placeText = computed(() =>
  [props.loc.canteenName, props.loc.floor, props.loc.stallName].filter(Boolean).join(' · '),
)
</script>

<template>
  <view class="card" hover-class="card--press">
    <view class="card__cover">
      <image v-if="src" class="card__img" :src="src" mode="aspectFill" @error="onError" />
      <view v-else class="card__ph"><ImagePlaceholder /></view>
    </view>

    <view class="card__info">
      <view class="card__head">
        <text class="card__name">{{ dish.name }}</text>
        <text class="card__price">{{ priceText }}</text>
      </view>

      <text
        v-if="dish.description"
        class="card__desc"
        :class="{ 'card__desc--clamp': needFull && !expanded, 'card__desc--full': expanded }"
      >{{ dish.description }}</text>

      <view
        v-if="needFull"
        class="card__more"
        hover-class="card__link--press"
        @tap.stop="emit('open', loc.id)"
      >{{ expanded ? '收起' : '展开' }}</view>

      <view class="card__meta">
        <view
          class="card__link"
          hover-class="card__link--press"
          @tap.stop="emit('feedback', dish.id)"
        >反馈</view>
        <text class="card__place">{{ placeText }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.card {
  box-sizing: border-box;
  width: 100%;
  overflow: hidden;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
  background: var(--bg-card);
}

.card--press {
  opacity: 0.7;
}

.card__cover {
  position: relative;
  height: 0;
  padding-top: 56.25%;
  overflow: hidden;
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

/* 信息区：单列三段 —— 头部 / 描述 / 底行 */
.card__info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  padding: var(--spacing-sm);
}

.card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.card__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--font-title);
  color: var(--text-title);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card__price {
  flex-shrink: 0;
  font-size: var(--font-h3);
  color: var(--brand);
}

.card__desc {
  font-size: var(--font-body);
  line-height: 1.5;
  color: var(--text-body);
  overflow-wrap: anywhere;
}

.card__desc--clamp {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.card__desc--full {
  animation: desc-in var(--duration-base) var(--ease-out);
}

@keyframes desc-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

/* 展开 / 反馈：视觉小字，命中区经 ::after 补到触达下限 */
.card__more,
.card__link {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: var(--control-height-sm);
  padding: 0 var(--spacing-md);
  font-size: var(--font-caption);
  color: var(--text-title);
}

.card__more {
  align-self: flex-start;
  padding-left: 0;
  color: var(--text-body);
}

.card__more::after,
.card__link::after {
  position: absolute;
  top: calc((var(--tap-target-size) - var(--control-height-sm)) / -2);
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  content: '';
}

.card__link--press {
  opacity: 0.6;
}

/* 底行：反馈在左下，位置在右并省略 */
.card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.card__meta .card__link {
  padding-left: 0;
  color: var(--text-title);
}

.card__place {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--font-caption);
  color: var(--text-body);
  text-align: right;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
