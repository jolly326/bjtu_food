<template>
  <view class="page stall-page">
    <Header :title="canteenName" showBack />

    <view class="stall-list" v-if="stallList.length > 0">
      <StallCard
        v-for="(stall, idx) in stallList"
        :key="idx"
        :stall="stall"
        @click="goToStall"
        @dish-click="goToDetail"
      />
    </view>

    <EmptyState v-else icon="🍽️" text="该食堂暂无档口数据" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import StallCard from '@/components/StallCard.vue'
import type { StallInfo, DishPreview } from '@/components/StallCard.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'
import type { CanteenInfo } from '@/stores/types'

const dishStore = useDishStore()
const canteenName = ref('')

const stallList = computed(() => {
  const all = dishStore.recommendList as unknown as Dish[]
  const filtered = all.filter(d => d.canteen === canteenName.value)

  const stallMap = new Map<string, Dish[]>()
  for (const dish of filtered) {
    if (!stallMap.has(dish.stallName)) {
      stallMap.set(dish.stallName, [])
    }
    stallMap.get(dish.stallName)!.push(dish)
  }

  const result: StallInfo[] = []
  for (const [name, stallDishes] of stallMap) {
    const canteenInfo = (dishStore.canteenList as unknown as CanteenInfo[]).find(c => c.name === canteenName.value)
    const totalRating = stallDishes.reduce((sum, d) => sum + (d.rating || 0), 0)
    const totalCount = stallDishes.reduce((sum, d) => sum + (d.ratingCount || 0), 0)
    const avgRating = totalCount > 0 ? totalRating / stallDishes.length : 0
    result.push({
      id: stallDishes[0]?.id ?? 0,
      name,
      location: canteenInfo?.location || '位置待定',
      dishCount: stallDishes.length,
      image: '/static/dish_placeholder.jpg',
      rating: avgRating,
      ratingCount: totalCount,
      dishes: stallDishes.map(d => ({
        id: d.id,
        name: d.name,
        price: d.price,
        image: d.image,
      })),
    })
  }
  return result
})

function goToStall(stall: StallInfo) {
  dishStore.navParams.stallName = stall.name
  dishStore.navParams.canteen = canteenName.value
  uni.navigateTo({ url: '/pages/stall/index' })
}

function goToDetail(dish: DishPreview) {
  uni.navigateTo({ url: `/pages/dish/detail?id=${dish.id}` })
}

onLoad(async (query) => {
  if (query?.canteen) {
    canteenName.value = decodeURIComponent(query.canteen as string)
  }
  if ((dishStore.recommendList as unknown as Dish[]).length === 0) {
    await dishStore.fetchRecommend()
  }
  if ((dishStore.canteenList as unknown as CanteenInfo[]).length === 0) {
    await dishStore.fetchCanteens()
  }
})
</script>

<style scoped>
.stall-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: 40rpx;
}
.stall-list {
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

</style>
