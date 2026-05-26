<script lang="ts">
export interface StallInfo {
  name: string
  location: string
  dishes: Dish[]
}

import type { Dish } from '@/types/dish'
</script>

<template>
  <view class="stall-card" @tap="handleClick">
    <view class="stall-header">
      <view class="stall-title-row">
        <text class="stall-icon">🏪</text>
        <text class="stall-name">{{ stall.name }}</text>
      </view>
      <text class="stall-location">📍 {{ stall.location }}</text>
    </view>
    <view class="stall-dishes">
      <view
        v-for="dish in visibleDishes"
        :key="dish.id"
        class="stall-dish-item"
        @tap.stop="goToDetail(dish)"
      >
        <view class="dish-img">
          <ImageFallback :src="dish.image" />
        </view>
        <view class="dish-info">
          <text class="dish-name">{{ dish.name }}</text>
          <view class="dish-meta">
            <text class="dish-price">¥{{ dish.price }}</text>
            <text class="dish-rating">⭐ {{ dish.rating }}</text>
          </view>
        </view>
      </view>
      <view class="stall-more" v-if="stall.dishes.length > 3">
        <text class="more-text">查看全部 {{ stall.dishes.length }} 道菜品 ›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ImageFallback from './ImageFallback.vue'

const props = defineProps<{
  stall: StallInfo
}>()

const emit = defineEmits<{
  click: [stall: StallInfo]
  dishClick: [dish: Dish]
}>()

const visibleDishes = computed(() => props.stall?.dishes?.slice(0, 3) || [])

function handleClick() {
  emit('click', props.stall)
}

function goToDetail(dish: Dish) {
  emit('dishClick', dish)
}
</script>

<style scoped>
.stall-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
}
.stall-header {
  padding: 20rpx var(--spacing-md);
  border-bottom: 2rpx solid var(--border-color);
}
.stall-title-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.stall-icon {
  font-size: 32rpx;
}
.stall-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary);
}
.stall-location {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  display: block;
  margin-top: 6rpx;
  padding-left: 40rpx;
}
.stall-dishes {
  padding: 0;
}
.stall-dish-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 2rpx solid var(--bg-page);
}
.stall-dish-item:last-child {
  border-bottom: none;
}
.dish-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  overflow: hidden;
  background: var(--bg-page);
  flex-shrink: 0;
}
.dish-img image {
  width: 100%;
  height: 100%;
}
.dish-info {
  flex: 1;
  min-width: 0;
}
.dish-name {
  font-size: var(--font-body);
  font-weight: 500;
  color: var(--text-primary);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: 8rpx;
}
.dish-price {
  font-size: var(--font-body);
  font-weight: 600;
  color: var(--color-price);
}
.dish-rating {
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
}
.stall-more {
  padding: 20rpx var(--spacing-md);
  text-align: center;
}
.more-text {
  font-size: 26rpx;
  color: var(--color-primary);
}
</style>
