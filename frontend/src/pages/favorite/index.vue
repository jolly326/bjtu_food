<template>
  <view class="page favorite-page">
    <Header title="我的收藏" />

    <!-- 收藏瀑布流 -->
    <view class="dish-section">
      <view v-if="favoriteList.length > 0">
        <WaterfallList :list="favoriteList" :key="favoriteList.length">
          <template #card="{ item: dish }">
            <DishCard :dish="dish" @click="goToDetail" />
          </template>
        </WaterfallList>
      </view>

      <!-- 空状态 -->
      <EmptyState v-else text="暂无收藏的菜品" hint="去首页逛逛，收藏喜欢的菜吧">
        <template #action>
          <AppButton text="去首页逛逛" type="gradient" width="auto" margin="0" @tap="goToHome" />
        </template>
      </EmptyState>
    </view>

    <CustomTabBar current="/pages/favorite/index" />
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import EmptyState from '@/components/EmptyState.vue'
import DishCard from '@/components/DishCard.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useFavoriteStore } from '@/stores/favorite'
import type { Dish } from '@/types/dish'

const favoriteStore = useFavoriteStore()
const favoriteList = computed(() => favoriteStore.favoriteList as unknown as Dish[])

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/dish/detail?id=${dish.id}` })
}

function goToHome() {
  uni.reLaunch({ url: '/pages/home/index' })
}

onMounted(() => {
  favoriteStore.fetchFavorites()
})
</script>

<style scoped>
.favorite-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}
.dish-section {
  padding: var(--spacing-md) 30rpx 0;
}



</style>
