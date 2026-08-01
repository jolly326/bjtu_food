<template>
  <view
    class="dish-card"
    :class="{ pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap="handleClick"
  >
    <view class="card-image">
      <image v-if="imgSrc && imgOk" :src="imgSrc" mode="aspectFill" class="card-img" @error="imgOk = false" />
      <view v-else class="image-placeholder">
        <IconSvg name="dish" :size="64" color="var(--text-tertiary)" class="placeholder-icon" />
      </view>
      <view class="card-rating-badge">
        <IconSvg name="star" :size="22" color="var(--color-star)" class="star-icon" />
        <text class="rating-text">{{ dish.rating }}</text>
      </view>
    </view>
    <view class="card-info">
      <text class="card-name">{{ dish.name }}</text>
      <view class="meta-row">
        <text class="card-stall">{{ dish.canteen }} · {{ dish.stallName }}</text>
        <text class="card-price">¥{{ dish.price }}</text>
      </view>
      <view class="card-tags" v-if="dish.tags.length > 0">
        <TagLabel v-for="tag in dish.tags" :key="tag" :text="tag" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Dish } from '@/types/dish'
import { getImageUrl } from '@/utils/image'
import IconSvg from './IconSvg.vue'
import TagLabel from './TagLabel.vue'

const props = defineProps<{
  dish: Dish
}>()

const emit = defineEmits<{
  click: [dish: Dish]
}>()

/** 按压反馈：按下时整体缩放到 0.97（跨端兼容，替代小程序不支持的 v-press 指令） */
const pressed = ref(false)

/** 图片 URL：通过 getImageUrl 处理（兼容相对路径与完整 URL） */
const imgSrc = computed(() => getImageUrl(props.dish.image))

/** 图片加载状态：加载失败则回退到占位，禁止裂图 */
const imgOk = ref(true)

function handleClick() {
  emit('click', props.dish)
}
</script>

<style scoped>
.dish-card {
  width: 100%;
  min-width: 0;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  /* 进场仅极轻量淡入 + 按压缩放（红线 §4.9②：位移 ≤0，仅 transform/opacity） */
  transition: transform 0.12s ease, opacity 0.2s ease;
  -webkit-tap-highlight-color: transparent;
}
.dish-card.pressed {
  transform: scale(0.97);
}
.card-image {
  position: relative;
  width: 100%;
  /* 高度由瀑布流注入的 --card-img-h 驱动（实现错落），默认 200rpx 兼容非瀑布流场景 */
  height: var(--card-img-h, 200rpx);
  background: var(--bg-page);
}
.card-img {
  width: 100%;
  height: 100%;
}
.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.placeholder-icon {
  font-size: 64rpx;
  line-height: 1;
}
.card-rating-badge {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  background: var(--overlay-dark-strong);
  border-radius: var(--radius-card);
  padding: var(--spacing-xs) var(--spacing-sm);
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}
.star-icon {
  font-size: 22rpx;
  line-height: 1;
  flex-shrink: 0;
}
.rating-text {
  color: var(--text-white);
  font-size: var(--font-tiny);
  font-weight: 700;
}
.card-info {
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  min-width: 0;
}
.card-name {
  display: block;
  font-size: var(--font-body);
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.01em;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-xs);
}
.card-stall {
  font-size: var(--font-aux);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.card-price {
  font-size: var(--font-caption);
  color: var(--color-price);
  font-weight: 700;
  flex-shrink: 0;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  margin-top: var(--spacing-sm);
}
</style>
