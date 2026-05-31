<template>
  <view class="page home-page">
    <Header title="食在交大" />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled @refresherrefresh="onRefresh">
      <SearchBar @tap="navigateToSearch" />

      <view class="swiper-section">
        <swiper class="home-swiper" indicator-dots indicator-color="var(--text-tertiary)"
          indicator-active-color="var(--color-primary)" autoplay interval="3000" circular>
          <swiper-item v-for="(item, idx) in dishStore.homeBanners" :key="idx">
            <view class="swiper-slide" :style="{ backgroundImage: `url(${item.image})` }">
              <view class="swiper-overlay" />
              <text class="swiper-title">{{ item.title }}</text>
              <text class="swiper-subtitle">{{ item.subtitle }}</text>
            </view>
          </swiper-item>
        </swiper>
      </view>

      <view class="canteen-section">
        <swiper class="canteen-swiper" circular :current="currentSwiperIndex"
          previous-margin="150rpx" next-margin="150rpx" @change="onSwiperChange">
          <swiper-item v-for="(item, idx) in canteens" :key="item.name">
            <view class="canteen-card" :class="{ active: currentSwiperIndex === idx }" @tap="goToCanteen(item.name)">
              <view class="canteen-overlay" />
              <text class="canteen-name">{{ item.name }}</text>
              <text class="canteen-count">{{ item.count }}个档口</text>
            </view>
          </swiper-item>
        </swiper>
      </view>

      <view class="dish-section">
        <view v-if="displayList.length > 0">
          <WaterfallList :list="displayList" :key="currentCanteen">
            <template #card="{ item: dish }">
              <DishCard :dish="dish" @click="goToDetail" />
            </template>
          </WaterfallList>
        </view>
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
    <CustomTabBar current="/pages/home/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Header from '@/components/header.vue'
import SearchBar from '@/components/SearchBar.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import DishCard from '@/components/DishCard.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'

const dishStore = useDishStore()

const currentCanteen = ref('')
const currentSwiperIndex = ref(0)

const canteens = computed(() => {
  const map = new Map<string, number>()
  for (const dish of dishStore.recommendList) {
    map.set(dish.canteen, (map.get(dish.canteen) || 0) + 1)
  }
  return Array.from(map).map(([name, count]) => ({
    name, image: dishStore.canteenImageMap[name] || '', count,
  }))
})

const displayList = computed(() =>
  dishStore.recommendList.filter((d) => d.canteen === currentCanteen.value)
)

async function loadData() {
  console.log('[home] loadData开始')
  await Promise.all([
    dishStore.fetchHomeBanners(),
    dishStore.fetchCanteenImages(),
    dishStore.fetchRecommend(),
  ])
  console.log('[home] loadData完成, canteens数量', canteens.value.length, '推荐列表长度', dishStore.recommendList.length)
  if (canteens.value.length > 0) {
    currentSwiperIndex.value = 0
    currentCanteen.value = canteens.value[0].name
    console.log('[home] 设置currentCanteen', currentCanteen.value)
  }
}

loadData()

function onRefresh(e: any) {
  loadData().finally(() => { e.detail.complete() })
}

function goToCanteen(name: string) {
  uni.navigateTo({ url: `/pages/pages-detail/canteen?canteen=${encodeURIComponent(name)}` })
}

function onSwiperChange(e: any) {
  const idx = e.detail.current
  currentSwiperIndex.value = idx
  const item = canteens.value[idx]
  if (item) currentCanteen.value = item.name
}

function navigateToSearch() {
  uni.navigateTo({ url: '/pages/find/index' })
}

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}
</script>

<style scoped>  
.home-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.swiper-section { padding: 0 var(--spacing-md); margin-bottom: var(--spacing-md); }
.home-swiper { height: 380rpx; border-radius: var(--radius-card); overflow: hidden; }
.swiper-slide { height: 100%; display: flex; flex-direction: column; justify-content: center; align-items: center; background-size: cover; background-position: center; position: relative; }
.swiper-overlay { position: absolute; inset: 0; background: linear-gradient(to top, rgba(0,0,0,0.6) 0%, rgba(0,0,0,0.15) 50%, rgba(0,0,0,0) 100%); }
.swiper-title { font-size: var(--font-h2); font-weight: bold; color: var(--text-white); margin-bottom: 10rpx; z-index: 1; }
.swiper-subtitle { font-size: var(--font-body); color: var(--text-white-secondary); z-index: 1; }
.canteen-section { margin-bottom: var(--spacing-md); }
.canteen-swiper { height: 280rpx; }
.canteen-card { height: 260rpx; border-radius: var(--radius-card); margin: 0 var(--spacing-sm); box-shadow: var(--shadow-card); position: relative; overflow: hidden; background-size: cover; background-position: center; }
.canteen-overlay { position: absolute; inset: 0; background: linear-gradient(to top, rgba(0,0,0,0.65) 0%, rgba(0,0,0,0.15) 50%, rgba(0,0,0,0) 100%); }
.canteen-name { position: absolute; left: var(--spacing-md); bottom: var(--spacing-md); font-size: var(--font-caption); font-weight: 600; color: var(--text-white); z-index: 1; }
.canteen-count { position: absolute; right: var(--spacing-md); bottom: 28rpx; font-size: var(--font-aux); color: var(--text-white-secondary); z-index: 1; }
.dish-section { padding: 0 var(--spacing-md); }
</style>
