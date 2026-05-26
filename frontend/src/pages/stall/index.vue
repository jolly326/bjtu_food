<template>
  <view class="page stall-detail-page">
    <Header :title="stallDetail?.name || '档口'" showBack />

    <template v-if="stallDetail">
      <!-- ===== 顶部：滚动横幅 ===== -->
      <swiper
        class="stall-swiper"
        indicator-dots
        indicator-color="rgba(255,255,255,0.4)"
        indicator-active-color="#FFFFFF"
        autoplay
        interval="4000"
        circular
      >
        <swiper-item v-for="(img, idx) in stallDetail.images" :key="idx">
          <image :src="img" mode="aspectFill" class="stall-swiper-img" />
        </swiper-item>
      </swiper>

      <!-- ===== 档口信息 ===== -->
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

      <!-- ===== 菜品列表：逐行展示 ===== -->
      <view class="dish-section">
        <text class="dish-section-title">全部菜品（{{ dishList.length }}）</text>
        <view v-if="dishList.length > 0" class="dish-list">
          <view
            v-for="dish in dishList"
            :key="dish.id"
            class="dish-row"
            @tap="goToDetail(dish)"
          >
            <view class="dish-row-img">
              <ImageFallback :src="dish.image" />
            </view>
            <view class="dish-row-info">
              <text class="dish-row-name">{{ dish.name }}</text>
              <view class="dish-row-meta">
                <image class="dish-row-star" src="/static/icons/star-active.svg" />
                <text class="dish-row-rating">{{ dish.rating }}</text>
              </view>
              <text class="dish-row-desc" v-if="dish.description">{{ dish.description }}</text>
            </view>
            <text class="dish-row-price">¥{{ dish.price }}</text>
          </view>
        </view>
        <EmptyState v-else icon="🍽️" text="该档口暂无菜品" />
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useDishStore } from '@/stores/dish'
import { getStallDetail } from '@/api/dish'
import type { StallDetail } from '@/stores/types'
import type { Dish } from '@/types/dish'

const dishStore = useDishStore()
const stallDetail = ref<StallDetail | null>(null)
const dishList = computed(() => dishStore.stallDishes as unknown as Dish[])

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/dish/detail?id=${dish.id}` })
}

onMounted(async () => {
  const { stallName, canteen } = dishStore.navParams
  if (stallName && canteen) {
    const [detail, _dishes] = await Promise.all([
      getStallDetail(canteen, stallName),
      dishStore.fetchStallDishes(canteen, stallName),
    ])
    stallDetail.value = detail
  }
})
</script>

<style scoped>
.stall-detail-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: 40rpx;
}

/* ===== 顶部轮播 ===== */
.stall-swiper {
  width: 100%;
  height: 400rpx;
}
.stall-swiper-img {
  width: 100%;
  height: 100%;
}

/* ===== 档口信息（名称 + 地址 + 介绍） ===== */
.info-section {
  margin: var(--spacing-md);
  padding: 24rpx;
  background: #FFFFFF;
  border-radius: var(--radius-card);
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.info-name {
  font-size: 34rpx;
  font-weight: 700;
  color: #1A1A1A;
  display: block;
  margin-bottom: 14rpx;
}
.info-location {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 14rpx;
  padding-bottom: 14rpx;
  border-bottom: 2rpx solid var(--bg-page);
}
.info-location-icon {
  width: 28rpx;
  height: 28rpx;
  flex-shrink: 0;
}
.info-location-text {
  font-size: 26rpx;
  color: #666666;
}
.info-desc-text {
  font-size: 26rpx;
  color: #666666;
  line-height: 1.6;
  display: block;
}

/* ===== 菜品列表 ===== */
.dish-section {
  margin: 0 var(--spacing-md);
}
.dish-section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1A1A1A;
  display: block;
  margin-bottom: var(--spacing-sm);
  padding-left: 8rpx;
}

.dish-list {
  background: #FFFFFF;
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.dish-row {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: 20rpx var(--spacing-sm);
  border-bottom: 2rpx solid var(--bg-page);
}
.dish-row:last-child {
  border-bottom: none;
}

.dish-row-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--bg-page);
}

.dish-row-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.dish-row-name {
  font-size: 30rpx;
  font-weight: 500;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish-row-meta {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.dish-row-star {
  width: 22rpx;
  height: 22rpx;
}

.dish-row-rating {
  font-size: 24rpx;
  color: #F5A623;
}

.dish-row-desc {
  font-size: 22rpx;
  color: #AAAAAA;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish-row-price {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-price, #E67E22);
  flex-shrink: 0;
  margin-left: 8rpx;
}
</style>
