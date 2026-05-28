<template>
  <view class="page stall-detail-page">
    <Header :title="stallDetail?.name || '档口'" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled @refresherrefresh="onRefresh">
      <template v-if="stallDetail">
        <ImageSwiper :images="stallDetail.images" />
        <view class="info-section">
          <text class="info-name">{{ stallDetail.name }}</text>
          <view class="info-location">
            <image class="info-location-icon" src="/static/icons/location.svg" />
            <text class="info-location-text">{{ stallDetail.location }}</text>
          </view>
          <view class="info-desc">
            <text class="info-desc-text">{{ stallDetail.description }}</text>
          </view>
        </view>
        <view class="dish-section">
          <text class="dish-section-title">全部菜品（{{ dishList.length }}）</text>
          <view v-if="dishList.length > 0" class="dish-list">
            <view v-for="dish in dishList" :key="dish.id" class="dish-row" @click="goToDetail(dish)">
              <view class="dish-row-img">
                <ImageFallback :src="dish.image" />
              </view>
              <view class="dish-row-info">
                <text class="dish-row-name">{{ dish.name }}</text>
                <view v-if="dish.tags?.length" class="dish-row-tags">
                  <TagLabel v-for="tag in dish.tags" :key="tag" :text="tag" />
                </view>
                <view class="dish-row-meta">
                  <image class="dish-row-star" src="/static/icons/star-yellow.svg" />
                  <text class="dish-row-rating">{{ dish.rating }}</text>
                </view>
              </view>
              <text class="dish-row-price">¥{{ dish.price }}</text>
            </view>
          </view>
        </view>
      </template>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import TagLabel from '@/components/TagLabel.vue'
import { useDishStore } from '@/stores/dish'
import { getStallDetail } from '@/api/canteen'
import type { StallDetail } from '@/types/canteen'
import type { Dish } from '@/types/dish'

const dishStore = useDishStore()
const stallDetail = ref<StallDetail | null>(null)
const dishList = computed(() => dishStore.stallDishes)

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

async function loadData() {
  const { stallName, canteen } = dishStore.navParams
  if (stallName && canteen) {
    const [detail] = await Promise.all([
      getStallDetail(canteen, stallName),
      dishStore.fetchStallDishes(canteen, stallName),
    ])
    stallDetail.value = detail
  }
}

onMounted(() => { loadData() })

function onRefresh(e: any) {
  loadData().finally(() => { e.detail.complete() })
}
</script>

<style scoped>
.stall-detail-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.info-section { margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); }
.info-name { font-size: var(--font-subtitle); font-weight: 700; color: var(--text-primary); display: block; margin-bottom: var(--spacing-sm); }
.info-location { display: flex; align-items: center; gap: var(--spacing-xs); margin-bottom: var(--spacing-sm); padding-bottom: var(--spacing-sm); border-bottom: 2rpx solid var(--bg-page); }
.info-location-icon { width: 28rpx; height: 28rpx; flex-shrink: 0; }
.info-location-text { font-size: var(--font-small); color: var(--text-secondary); }
.info-desc-text { font-size: var(--font-small); color: var(--text-secondary); line-height: 1.6; display: block; }
.dish-section { margin: 0 var(--spacing-md); }
.dish-section-title { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); display: block; margin-bottom: var(--spacing-sm); padding-left: var(--spacing-xs); }
.dish-list { background: var(--bg-card); border-radius: var(--radius-card); overflow: hidden; }
.dish-row { display: flex; align-items: flex-start; gap: var(--spacing-sm); padding: var(--spacing-md) var(--spacing-sm); border-bottom: 2rpx solid var(--bg-page); }
.dish-row:last-child { border-bottom: none; }
.dish-row-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-tag); overflow: hidden; flex-shrink: 0; background: var(--bg-page); }
.dish-row-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.dish-row-name { font-size: var(--font-caption); font-weight: 500; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dish-row-tags { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); }
.dish-row-meta { display: flex; align-items: center; gap: 4rpx; }
.dish-row-star { width: 32rpx; height: 32rpx; }
.dish-row-rating { font-size: var(--font-card); color: var(--color-star); }
.dish-row-price { font-size: var(--font-card); font-weight: 700; color: var(--color-price); flex-shrink: 0; margin-left: var(--spacing-xs); }
</style>
