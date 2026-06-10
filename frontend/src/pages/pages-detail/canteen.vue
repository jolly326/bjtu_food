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
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import StallCard from '@/components/StallCard.vue'
import type { StallInfo, DishPreview } from '@/components/StallCard.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'
import { getCanteensWithStalls } from '@/api/canteen'
import { getStallDishes } from '@/api/dish'

const dishStore = useDishStore()
const canteenName = ref('')
const stallList = ref<StallInfo[]>([])

function firstImage(value: unknown): string {
  return Array.isArray(value) ? (value.find(item => typeof item === 'string') || '') : ''
}

async function loadStalls() {
  if (!canteenName.value) return
  const canteens = await getCanteensWithStalls()
  const current = canteens.find((item: any) => item.name === canteenName.value)
  const stalls = current?.stalls || []
  stallList.value = await Promise.all(stalls.map(async (stall: any) => {
    let dishes: Dish[] = []
    try {
      dishes = await getStallDishes(canteenName.value, stall.name)
    } catch {
      dishes = []
    }
    return {
      id: Number(stall.id || 0),
      name: stall.name || '',
      location: stall.location || current?.location || canteenName.value,
      dishCount: dishes.length,
      image: firstImage(stall.images),
      rating: stall.avgRating ?? stall.rating ?? 0,
      ratingCount: dishes.reduce((sum, d) => sum + (d.ratingCount || 0), 0),
      dishes: dishes.slice(0, 10).map(d => ({
        id: d.id,
        name: d.name,
        price: d.price,
        image: d.image,
      })),
    }
  }))
}

function goToStall(stall: StallInfo) {
  dishStore.navParams.stallName = stall.name
  dishStore.navParams.canteen = canteenName.value
  uni.navigateTo({ url: '/pages/pages-detail/stall' })
}

function goToDetail(dish: DishPreview) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

onLoad(async (query) => {
  if (query?.canteen) {
    canteenName.value = decodeURIComponent(query.canteen as string)
  }
  await loadStalls()
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
