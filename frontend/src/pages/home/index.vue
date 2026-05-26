<template>
  <view class="page home-page">
    <Header title="食在交大" />

    <!-- 搜索框（点击跳转） -->
    <SearchBar @tap="navigateToSearch" />

    <!-- 轮播图 -->
    <view class="swiper-section">
      <swiper
        class="home-swiper"
        indicator-dots
        indicator-color="#CCCCCC"
        indicator-active-color="#8B3A2B"
        autoplay
        interval="3000"
        circular
      >
        <swiper-item v-for="(item, idx) in swiperList" :key="idx">
          <view class="swiper-slide" :style="{ backgroundImage: `url(${item.image})` }">
            <view class="swiper-overlay" />
            <text class="swiper-title">{{ item.title }}</text>
            <text class="swiper-subtitle">{{ item.subtitle }}</text>
          </view>
        </swiper-item>
      </swiper>
    </view>

    <!-- 食堂入口（闭环滚动 · 中央选中） -->
    <view class="canteen-section">
      <swiper
        class="canteen-swiper"
        circular
        :current="currentSwiperIndex"
        previous-margin="150rpx"
        next-margin="150rpx"
        @change="onSwiperChange"
      >
        <swiper-item v-for="(item, idx) in canteens" :key="item.name">
          <view
            class="canteen-card"
            :class="{ active: currentSwiperIndex === idx }"
            :style="canteenStyle(item)"
            @tap="goToCanteen(item.name)"
          >
            <view class="canteen-overlay" />
            <text class="canteen-name">{{ item.name }}</text>
            <text class="canteen-count">{{ item.count }}个档口</text>
          </view>
        </swiper-item>
      </swiper>
    </view>

    <!-- 菜品瀑布流 -->
    <view class="dish-section">
      <view v-if="displayList.length > 0">
        <WaterfallList :list="displayList" :key="currentCanteen">
          <template #card="{ item: dish }">
            <DishCard :dish="dish" @click="goToDetail" />
          </template>
        </WaterfallList>
      </view>
      <EmptyState v-else text="该食堂暂无菜品数据" />
    </view>

    <CustomTabBar current="/pages/home/index" />
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import Header from '@/components/header.vue'
import SearchBar from '@/components/SearchBar.vue'
import EmptyState from '@/components/EmptyState.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import DishCard from '@/components/DishCard.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'
import { getHomeBanners, getCanteenImages } from '@/api/dish'
import type { BannerItem } from '@/stores/types'

const dishStore = useDishStore()

// ==================== 横幅轮播 ====================
const swiperList = ref<BannerItem[]>([])

// ==================== 食堂滚动 ====================
const currentCanteen = ref('')
const currentSwiperIndex = ref(0)

/** 食堂背景图片映射 { 食堂名 → 图片路径 } */
const canteenImageMap = ref<Record<string, string>>({})

/** 食堂列表（从菜品数据中提取 + 背景图） */
const canteens = computed(() => {
  const list = dishStore.recommendList as unknown as Dish[]
  const seen = new Set<string>()
  const result: { name: string; image: string; count: number }[] = []
  for (const dish of list) {
    if (!seen.has(dish.canteen)) {
      seen.add(dish.canteen)
      const count = list.filter((d: Dish) => d.canteen === dish.canteen).length
      result.push({
        name: dish.canteen,
        image: canteenImageMap.value[dish.canteen] || '',
        count,
      })
    }
  }
  return result
})

// 当前食堂的菜品
const displayList = computed(() => {
  const list = dishStore.recommendList as unknown as Dish[]
  return list.filter((d: Dish) => d.canteen === currentCanteen.value)
})

function canteenStyle(item: { image?: string }) {
  if (item.image) {
    return { backgroundImage: `url(${item.image})` }
  }
  return { backgroundImage: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }
}

function goToCanteen(name: string) {
  uni.navigateTo({ url: `/pages/canteen/index?canteen=${encodeURIComponent(name)}` })
}

function onSwiperChange(e: any) {
  const idx = e.detail.current
  currentSwiperIndex.value = idx
  const item = canteens.value[idx]
  if (item) {
    currentCanteen.value = item.name
  }
}

function navigateToSearch() {
  uni.navigateTo({ url: '/pages/find/index' })
}

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/dish/detail?id=${dish.id}` })
}

onMounted(async () => {
  // 并发加载首页所需数据
  const [banners, canteenImages] = await Promise.all([
    getHomeBanners(),
    getCanteenImages(),
    dishStore.fetchRecommend(),
  ])
  swiperList.value = banners
  canteenImageMap.value = canteenImages

  // 数据加载完后，默认选中第一张食堂卡片
  if (canteens.value.length > 0) {
    currentSwiperIndex.value = 0
    currentCanteen.value = canteens.value[0].name
  }
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}

.swiper-section {
  padding: 0 var(--spacing-md);
  margin-bottom: var(--spacing-md);
}
.home-swiper {
  height: 300rpx;
  border-radius: var(--radius-card);
  overflow: hidden;
}
.swiper-slide {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-size: cover;
  background-position: center;
  position: relative;
}
.swiper-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.6) 0%,
    rgba(0, 0, 0, 0.15) 50%,
    rgba(0, 0, 0, 0) 100%
  );
}
.swiper-title {
  font-size: var(--font-h2);
  font-weight: bold;
  color: var(--text-white);
  margin-bottom: 10rpx;
  z-index: 1;
}
.swiper-subtitle {
  font-size: var(--font-body);
  color: var(--text-white-secondary);
  z-index: 1;
}

.canteen-section {
  margin-bottom: var(--spacing-md);
}
.canteen-swiper {
  height: 280rpx;
}
.canteen-card {
  height: 260rpx;
  border-radius: var(--radius-card);
  margin: 0 var(--spacing-sm);
  box-shadow: var(--shadow-card);
  position: relative;
  overflow: hidden;
  background-size: cover;
  background-position: center;
}
.canteen-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.65) 0%,
    rgba(0, 0, 0, 0.15) 50%,
    rgba(0, 0, 0, 0) 100%
  );
}
.canteen-name {
  position: absolute;
  left: var(--spacing-md);
  bottom: var(--spacing-md);
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-white);
  z-index: 1;
}
.canteen-count {
  position: absolute;
  right: var(--spacing-md);
  bottom: 28rpx;
  font-size: var(--font-aux);
  color: var(--text-white-secondary);
  z-index: 1;
}

.dish-section {
  padding: 0 var(--spacing-md);
}
</style>
