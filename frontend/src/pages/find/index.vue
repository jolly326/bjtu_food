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
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
    <CustomTabBar current="/pages/find/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
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
  dishStore.search({ keyword: value })
}

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

function onRefresh(e: any) {
  if (keyword.value) {
    dishStore.search({ keyword: keyword.value }).finally(() => { e.detail.complete() })
  } else {
    e.detail.complete()
  }
}
</script>

<style scoped>
.search-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.result-section { padding: 0 var(--spacing-lg); }
</style>
