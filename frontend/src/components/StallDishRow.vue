<template>
  <view
    class="dish-row"
    :class="{ pressed: pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap="onClick"
  >
    <view class="dish-row-img">
      <ImageFallback :src="dish.image" />
    </view>
    <view class="dish-row-info">
      <text class="dish-row-name">{{ dish.name }}</text>
      <text v-if="dish.description" class="dish-row-desc">{{ dish.description }}</text>
      <view v-if="dish.tags?.length" class="dish-row-tags">
        <TagLabel v-for="tag in dish.tags" :key="tag" :text="tag" />
      </view>
      <view class="dish-row-meta">
        <view class="star-num">
          <IconSvg name="star-filled" :size="22" color="var(--color-star)" />
          <text class="star-num-text">{{ dish.rating }}</text>
        </view>
      </view>
    </view>
    <text class="dish-row-price">¥{{ dish.price }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ImageFallback from '@/components/ImageFallback.vue'
import TagLabel from '@/components/TagLabel.vue'
import IconSvg from '@/components/IconSvg.vue'
import type { Dish } from '@/types/dish'

const props = defineProps<{ dish: Dish }>()
const emit = defineEmits<{ (e: 'click', dish: Dish): void }>()

const pressed = ref(false)

function onClick() {
  emit('click', props.dish)
}
</script>

<style scoped>
.dish-row { display: flex; align-items: flex-start; gap: var(--spacing-sm); padding: var(--spacing-md) var(--spacing-sm); border-bottom: 2rpx solid var(--border-color); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.dish-row.pressed { transform: scale(var(--press-scale)); }
.dish-row:last-child { border-bottom: none; }
.dish-row-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-card); overflow: hidden; flex-shrink: 0; background: var(--bg-page); }
.dish-row-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.dish-row-name { font-size: var(--font-caption); font-weight: 500; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dish-row-desc { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1.4; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 1; overflow: hidden; }
.dish-row-tags { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); }
.dish-row-meta { display: flex; align-items: center; gap: var(--spacing-xs); }
.star-num { display: inline-flex; align-items: center; gap: 4rpx; }
.star-num-text { font-size: 24rpx; color: var(--text-secondary); font-weight: 600; }
.dish-row-price { font-size: var(--font-card); font-weight: 700; color: var(--color-price); flex-shrink: 0; margin-left: var(--spacing-xs); font-variant-numeric: tabular-nums; }
</style>
