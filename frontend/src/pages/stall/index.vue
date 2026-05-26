<template>
  <view class="page stall-page">
    <Header :title="stallName" showBack />

    <CardSection title="全部菜品">
      <view class="dish-list" v-if="dishList.length > 0">
        <view v-for="dish in dishList" :key="dish.id" class="dish-card" @tap="goToDetail(dish)">
          <view class="dish-img">
            <ImageFallback :src="dish.image" />
          </view>
          <view class="dish-info">
            <text class="dish-name">{{ dish.name }}</text>
            <view class="dish-meta">
              <text class="dish-rating">⭐ {{ dish.rating }}</text>
              <text class="dish-price">¥{{ dish.price }}</text>
            </view>
            <view class="dish-tags" v-if="dish.tags.length > 0">
              <TagLabel v-for="tag in dish.tags" :key="tag" :text="tag" />
            </view>
          </view>
        </view>
      </view>
      <EmptyState v-else icon="🍽️" text="该档口暂无菜品" />
    </CardSection>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'

const dishStore = useDishStore()

const stallName = computed(() => dishStore.navParams.stallName)
const dishList = computed(() => dishStore.stallDishes as unknown as Dish[])
const hasLoaded = ref(false)

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/dish/detail?id=${dish.id}` })
}

onShow(() => {
  const { stallName: s, canteen: c } = dishStore.navParams
  if (s && c && !hasLoaded.value) {
    hasLoaded.value = true
    dishStore.fetchStallDishes(c, s)
  }
})
</script>

<style scoped>
.stall-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: 40rpx;
}
.dish-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.dish-card {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
  border-bottom: 2rpx solid var(--bg-page);
}
.dish-card:last-child {
  border-bottom: none;
}
.dish-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--bg-page);
}
.dish-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6rpx;
}
.dish-name {
  font-size: var(--font-body);
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.dish-rating {
  font-size: 24rpx;
  color: var(--text-secondary);
}
.dish-price {
  font-size: var(--font-body);
  font-weight: 600;
  color: var(--color-price);
}
.dish-tags {
  display: flex;
  gap: 6rpx;
  flex-wrap: wrap;
}
</style>
