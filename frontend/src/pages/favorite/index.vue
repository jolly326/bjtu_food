<template>
  <view class="page favorite-page">
    <Header title="我的收藏" />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled @refresherrefresh="onRefresh">
      <view class="dish-section">
        <view v-if="favoriteList.length > 0">
          <WaterfallList :list="favoriteList" :key="favoriteList.length">
            <template #card="{ item: dish }">
              <DishCard :dish="dish" @click="goToDetail" />
            </template>
          </WaterfallList>
        </view>
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
    <CustomTabBar current="/pages/favorite/index" />
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import DishCard from '@/components/DishCard.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useFavoriteStore } from '@/stores/favorite'
import type { Dish } from '@/types/dish'

const favoriteStore = useFavoriteStore()
const favoriteList = computed(() => favoriteStore.favoriteList)

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

onMounted(() => { favoriteStore.fetchFavorites() })

function onRefresh(e: any) {
  favoriteStore.fetchFavorites().finally(() => { e.detail.complete() })
}
</script>

<style scoped>
.favorite-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.dish-section { padding: var(--spacing-md) var(--spacing-lg) 0; }
</style>
