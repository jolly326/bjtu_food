<template>
  <view class="dish-card" @tap="handleClick">
    <view class="card-image">
      <image v-if="imgSrc" :src="imgSrc" mode="aspectFill" />
      <view v-else class="image-placeholder">
        <text class="placeholder-icon">🍽️</text>
      </view>
      <view class="card-rating-badge">
        <image class="star-icon" src="/static/icons/star-active.svg" />
        <text class="rating-text">{{ dish.rating }}</text>
      </view>  
    </view>
    <view class="card-info">
      <view class="name-row">
        <text class="card-name">{{ dish.name }}</text>
        <text class="card-price">¥{{ dish.price }}</text>
      </view>
      <text class="card-stall">{{ dish.canteen }} · {{ dish.stallName }}</text>
      <view class="card-tags" v-if="dish.tags.length > 0">
        <TagLabel v-for="tag in dish.tags" :key="tag" :text="tag" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Dish } from '@/types/dish'
import { getImageUrl } from '@/utils/image'
import TagLabel from './TagLabel.vue'

const props = defineProps<{
  dish: Dish
}>()

const emit = defineEmits<{
  click: [dish: Dish]
}>()

/** 图片 URL：通过 getImageUrl 处理（兼容相对路径与完整 URL） */
const imgSrc = computed(() => getImageUrl(props.dish.image))

function handleClick() {
  emit('click', props.dish)
}
</script>

<style scoped>
.dish-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  border: 2rpx solid var(--border-color);
}
.card-image {
  position: relative;
  width: 100%;
  height: 200rpx;
  background: var(--bg-page);
}
.card-image image {
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
}
.card-rating-badge {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 20rpx;
  padding: 4rpx 12rpx;
  display: flex;
  align-items: center;
  gap: 4rpx;
}
image.star-icon {
  width: 24rpx;
  height: 24rpx;
  flex-shrink: 0;
}
.rating-text {
  color: #fff;
  font-size: var(--font-tiny);
  font-weight: bold;
}
.card-info {
  padding: 20rpx var(--spacing-md);
}
.name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}
.card-name {
  font-size: var(--font-body);
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.card-price {
  font-size: 30rpx;
  color: var(--color-price);
  font-weight: bold;
  flex-shrink: 0;
}
.card-stall {
  font-size: var(--font-aux);
  color: var(--text-secondary);
  margin-top: var(--spacing-xs);
  display: block;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6rpx;
  margin-top: var(--spacing-xs);
}
</style>
