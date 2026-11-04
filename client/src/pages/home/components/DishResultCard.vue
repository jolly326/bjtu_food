<script setup lang="ts">
/**
 * 结果卡（docs/ui/client/菜品首页.md `DishResultCard`）。
 *
 * 一卡 = 一道菜的一个在售位置：16:9 封面 + 左内容 / 右数据两栏信息区；
 * 图文与价格取自菜品本体，食堂 / 楼层 / 档口取自位置。
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
const placeText = computed(() => {
  const floor = props.loc.floor
  const stall = props.loc.stallName
  if (!floor && !stall) return '暂无位置'
  return `${floor || '暂无'} · ${stall || '暂无'}`
})
</script>

<template>
  <view class="card" hover-class="card--press">
    <view class="card__cover">
      <image v-if="src" class="card__img" :src="src" mode="aspectFill" @error="onError" />
      <view v-else class="card__ph"><ImagePlaceholder /></view>
    </view>

    <view class="card__info">
      <view class="card__main">
        <text class="card__name">{{ dish.name }}</text>

        <text
          v-if="dish.description"
          class="card__desc"
          :class="{ 'card__desc--clamp': needFull && !expanded, 'card__desc--full': expanded }"
        >{{ dish.description }}</text>

        <view class="card__actions">
          <view
            v-if="needFull"
            class="card__link"
            hover-class="card__link--press"
            @tap.stop="emit('open', loc.id)"
          >{{ expanded ? '收起' : '展开' }}</view>
          <view
            class="card__link"
            hover-class="card__link--press"
            @tap.stop="emit('feedback', dish.id)"
          >反馈</view>
        </view>
      </view>

      <view class="card__side">
        <text class="card__price">{{ priceText }}</text>
        <text class="card__canteen">{{ loc.canteenName }}</text>
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

/* 信息区：左内容 / 右数据两栏，靠留白分组，无分割线 */
.card__info {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-md);
  box-sizing: border-box;
  padding: var(--spacing-sm);
}

.card__main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.card__side {
  display: flex;
  flex-shrink: 0;
  width: var(--card-side-width);
  flex-direction: column;
  align-items: flex-end;
  gap: var(--spacing-xs);
  text-align: right;
}

.card__name {
  overflow: hidden;
  font-size: var(--font-title);
  color: var(--text-title);
  white-space: nowrap;
  text-overflow: ellipsis;
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

.card__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

/* 文字链：视觉高 64rpx，命中区经 ::after 纵向补到触达下限 */
.card__link {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: var(--control-height-sm);
  padding: 0 var(--spacing-md);
  font-size: var(--font-caption);
  color: var(--text-title);
}

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

.card__price {
  font-size: var(--font-h3);
  color: var(--brand);
}

.card__canteen {
  width: 100%;
  overflow: hidden;
  font-size: var(--font-caption);
  color: var(--text-title);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card__place {
  overflow: hidden;
  width: 100%;
  font-size: var(--font-caption);
  color: var(--text-body);
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
