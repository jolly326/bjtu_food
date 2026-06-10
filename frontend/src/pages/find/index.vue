<template>
  <view class="page search-page">
    <Header title="发现"/>
    <SearchBar input-mode margin="20rpx 32rpx" v-model="keyword" @search="handleSearch" />
    
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled @refresherrefresh="onRefresh">
      <view class="result-section">
        <view v-if="dishList.length > 0">
          <WaterfallList :list="dishList" :key="dishList.length">
            <template #card="{ item: dish }">
              <DishCard :dish="dish" @click="goToDetail" />
            </template>
          </WaterfallList>
        </view>
        <view v-else class="empty-state">
          <image class="empty-icon" src="/static/icons/search.svg" />
          <text class="empty-title">{{ dishStore.loading ? '正在加载...' : '暂无菜品' }}</text>
          <text class="empty-desc">{{ keyword ? '换个关键词试试' : '下拉刷新或稍后再试' }}</text>
        </view>
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
    <CustomTabBar current="/pages/find/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import Header from '@/components/header.vue'
import SearchBar from '@/components/SearchBar.vue'
import DishCard from '@/components/DishCard.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'

const dishStore = useDishStore()
const keyword = ref('')
const dishList = computed(() => dishStore.dishList)

function handleSearch(value: string) {
  keyword.value = value
  dishStore.search({ keyword: value.trim() || undefined })
}

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

function onRefresh(e: any) {
  dishStore.search({ keyword: keyword.value.trim() || undefined }).finally(() => { e.detail.complete() })
}

watch(keyword, (value) => {
  if (!value.trim()) {
    dishStore.search({})
  }
})

onMounted(() => {
  dishStore.search({})
})
</script>

<style scoped>
.search-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.result-section { padding: 0 var(--spacing-lg); }
.empty-state { min-height: 520rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80rpx 30rpx; box-sizing: border-box; }
.empty-icon { width: 88rpx; height: 88rpx; opacity: .28; margin-bottom: 24rpx; }
.empty-title { font-size: 30rpx; font-weight: 650; color: var(--text-secondary); }
.empty-desc { margin-top: 10rpx; font-size: 24rpx; color: var(--text-tertiary); }
</style>
