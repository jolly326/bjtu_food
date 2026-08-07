<template>
  <!-- 单列菜品卡（美团外卖式，2026-08-03 抽出供档口页 / 发现结果页复用）：左正方形圆角图 + 右 名称/价格/标徽/评分 -->
  <view
    class="dish-row-card"
    :class="{ pressed: pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap="emit('card-click', dish)"
  >
    <view class="dish-row-thumb">
      <image
        v-if="dish.image && imgOk"
        :src="getImageUrl(dish.image)"
        mode="aspectFill"
        class="dish-row-img"
        lazy-load
        @error="imgOk = false"
      />
      <view v-else class="dish-row-placeholder">
        <IconSvg name="dish" :size="48" color="var(--text-tertiary)" />
      </view>
    </view>
    <view class="dish-row-info">
      <view class="dish-row-title-row">
        <text class="dish-row-name">{{ dish.name }}</text>
        <text class="dish-row-price">¥{{ dish.price }}</text>
      </view>
      <view class="dish-row-tags" v-if="dish.tags && dish.tags.length > 0">
        <text v-for="t in dish.tags" :key="t" class="dish-row-tag">{{ t }}</text>
      </view>
      <view class="dish-row-rating-line" v-if="dish.rating > 0">
        <IconSvg name="star-filled" :size="20" color="var(--color-star)" class="dish-row-star" />
        <text class="dish-row-rating-text">{{ dish.rating.toFixed(1) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import IconSvg from './IconSvg.vue'
import { getImageUrl } from '@/utils/image'
import type { Dish } from '@/types/dish'

const props = defineProps<{
  /** 菜品 */
  dish: Dish
}>()

const emit = defineEmits<{ (e: 'card-click', dish: Dish): void }>()

const pressed = ref(false)
const imgOk = ref(true)
</script>

<style scoped>
/* 单列菜品卡：一行一个，左图右信息（美团外卖式，小圆角） */
.dish-row-card {
  display: flex;
  align-items: stretch;
  gap: var(--spacing-sm);
  background: var(--bg-card);
  border-radius: 12rpx;
  padding: var(--spacing-sm);
  box-shadow: var(--shadow-card);
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
  /* 注意：微信小程序 WXSS 不支持相邻兄弟选择器（+），改用自身 margin-bottom 实现卡片间距 */
  margin-bottom: var(--spacing-md);
}
.dish-row-card.pressed { transform: scale(var(--press-scale)); }
/* 左图：正方形小圆角缩略图 */
.dish-row-thumb {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
  border-radius: 8rpx;
  overflow: hidden;
  background: var(--bg-page);
}
.dish-row-img { width: 100%; height: 100%; }
.dish-row-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 右信息：名称+价格 / 标徽 / 评分 */
.dish-row-info { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; padding: 2rpx 0; }
.dish-row-title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.dish-row-name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-row-price { font-size: var(--font-caption); color: var(--color-price); font-weight: var(--weight-bold); flex-shrink: 0; font-variant-numeric: tabular-nums; }
.dish-row-tags { display: flex; flex-wrap: wrap; gap: 6rpx; margin-top: 8rpx; }
.dish-row-tag {
  font-size: var(--font-tiny);
  color: var(--color-primary);
  background: var(--color-primary-soft);
  padding: 2rpx 10rpx;
  border-radius: var(--radius-tag);
  font-weight: var(--weight-semibold);
}
.dish-row-rating-line { display: flex; align-items: center; gap: var(--spacing-2xs); margin-top: var(--spacing-xs); }
.dish-row-star { font-size: 20rpx; line-height: 1; flex-shrink: 0; }
.dish-row-rating-text { font-size: var(--font-small); color: var(--text-secondary); font-weight: var(--weight-bold); font-variant-numeric: tabular-nums; }
</style>
