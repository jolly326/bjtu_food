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
          <view class="swiper-slide" :style="{ background: item.bg }">
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
        previous-margin="200rpx"
        next-margin="200rpx"
        @change="onSwiperChange"
      >
        <swiper-item v-for="(item, idx) in canteens" :key="item.name">
          <view
            class="canteen-card"
            :class="{ active: currentSwiperIndex === idx }"
            @tap="goToCanteen(item.name)"
          >
            <text class="canteen-icon">{{ item.icon }}</text>
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

const dishStore = useDishStore()

const swiperList = [
  { title: '🍜 交大美食季', subtitle: '发现校园里的每一道美味', bg: '#4e4646' },
  { title: '🔥 新菜品上架', subtitle: '一食堂二层新窗口开业', bg: '#5a3e2b' },
  { title: '🏆 热门排行', subtitle: '同学们都在吃什么', bg: '#3d4a3d' },
]

const currentCanteen = ref('')
const currentSwiperIndex = ref(0)

// 从菜品数据提取食堂列表
const canteens = computed(() => {
  const list = dishStore.recommendList as unknown as Dish[]
  const iconMap: Record<string, string> = {
    '第一食堂': '🍜',
    '第二食堂': '🍛',
    '第三食堂': '🥗',
  }
  const seen = new Set<string>()
  const result: { name: string; icon: string; count: number }[] = []
  for (const dish of list) {
    if (!seen.has(dish.canteen)) {
      seen.add(dish.canteen)
      const count = list.filter((d: Dish) => d.canteen === dish.canteen).length
      result.push({ name: dish.canteen, icon: iconMap[dish.canteen] || '🍴', count })
    }
  }
  return result
})

// 当前食堂的菜品
const displayList = computed(() => {
  const list = dishStore.recommendList as unknown as Dish[]
  return list.filter((d: Dish) => d.canteen === currentCanteen.value)
})

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
  await dishStore.fetchRecommend()
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
}
.swiper-title {
  font-size: 36rpx;
  font-weight: bold;
  color: var(--text-white);
  margin-bottom: 10rpx;
}
.swiper-subtitle {
  font-size: 28rpx;
  color: var(--text-white-secondary);
}

.canteen-section {
  margin-bottom: var(--spacing-md);
}
.canteen-swiper {
  height: 240rpx;
}
.canteen-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 220rpx;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  margin: 0 16rpx;
  box-shadow: var(--shadow-card);
  position: relative;
}
.canteen-card.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 40rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}
.canteen-icon {
  font-size: 48rpx;
  margin-top: 6rpx;
}
.canteen-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary);
  margin-top: 10rpx;
}
.canteen-card.active .canteen-name {
  color: var(--color-primary);
}
.canteen-count {
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
  margin-top: 6rpx;
}

.dish-section {
  padding: 0 var(--spacing-md);
}



</style>
