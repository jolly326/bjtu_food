<template>
  <view class="page search-page">
    <Header title="发现"/>

    <!-- 搜索输入 -->
    <view class="search-header">
      <view class="search-input-wrap">
        <image src="/static/icons/search.svg" class="search-input-icon" />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索菜品或档口..."
          confirm-type="search"
          @confirm="handleSearch"
          focus
        />
        <text v-if="keyword" class="clear-btn" @tap="keyword = ''">✕</text>
      </view>
    </view>

    <!-- 搜索结果瀑布流 -->
    <view class="result-section">
      <view v-if="dishList.length > 0">
        <WaterfallList :list="dishList" :key="dishList.length">
          <template #card="{ item: dish }">
            <DishCard :dish="dish" @click="goToDetail" />
          </template>
        </WaterfallList>
      </view>
      <EmptyState v-else-if="searched" icon="🔍" text="未找到相关菜品" />
    </view>

    <CustomTabBar current="/pages/find/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Header from '@/components/header.vue'
import DishCard from '@/components/DishCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'

const dishStore = useDishStore()

const keyword = ref('')
const searched = ref(false)
const dishList = computed(() => dishStore.dishList as unknown as Dish[])

function handleSearch() {
  searched.value = true
  dishStore.search({ keyword: keyword.value })
}

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/dish/detail?id=${dish.id}` })
}
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}
.search-header {
  padding: 20rpx 30rpx;
}
.search-input-wrap {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border-radius: var(--radius-btn);
  padding: 16rpx 24rpx;
  border: 2rpx solid var(--border-color);
  box-shadow: var(--shadow-card);
}
.search-input-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 12rpx;
}
.search-input {
  flex: 1;
  font-size: 28rpx;
}
.clear-btn {
  color: var(--text-tertiary);
  font-size: 28rpx;
  padding: 0 8rpx;
}
.result-section {
  padding: 0 30rpx;
}

</style>
