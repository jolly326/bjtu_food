<template>
  <view class="page detail-page">
    <Header title="菜品详情" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled @refresherrefresh="onRefresh">
      <template v-if="dish">
        <ImageSwiper :images="dish.images || [dish.image]" />

        <CardSection>
          <view class="title-row">
            <view class="title-left">
              <text class="dish-name">{{ dish.name }}</text>
              <TagLabel v-if="dish.tags.includes('必吃推荐')" text="必吃推荐" />
            </view>
            <text class="price-text">¥{{ dish.price }}</text>
          </view>

          <view class="rating-row">
            <view class="rating-left">
              <image class="star-icon-img" src="/static/icons/star-yellow.svg" />
              <text class="rating-value">{{ dish.rating }}</text>
            </view>
            <view class="fav-btn" @tap="toggleFavorite">
              <image :src="isFavorited ? '/static/icons/heart-active.svg' : '/static/icons/heart.svg'" class="fav-icon" />
            </view>
          </view>

          <view class="location-row" @tap="goToStall">
            <image class="location-icon-img" src="/static/icons/location.svg" />
            <text class="location-text">{{ dish.canteen }} · {{ dish.stallName }}</text>
            <image class="arrow-right" src="/static/icons/right.svg" />
          </view>
        </CardSection>

        <CardSection title="菜品介绍">
          <text class="desc-content">{{ dish.description }}</text>
        </CardSection>

        <CardSection>
          <view class="review-header-row">
            <text class="review-title">用户评价 ({{ reviewList.length }})</text>
          </view>
          <view class="review-list" v-if="reviewList.length > 0">
            <view v-for="rv in reviewList" :key="rv.id" class="review-item">
              <view class="review-header">
                <image v-if="rv.userAvatar" class="review-avatar" :src="getImageUrl(rv.userAvatar)" mode="aspectFill" />
                <view v-else class="review-avatar review-avatar-empty">
                  <image class="review-avatar-fallback" src="/static/icons/food.svg" />
                </view>
                <view class="review-header-right">
                  <view class="review-header-top">
                    <text class="review-name">{{ rv.userName }}</text>
                    <text class="review-time">{{ relativeTime(rv.createTime) }}</text>
                  </view>
                  <view class="review-stars">
                    <image v-for="i in starCount(rv.rating)" :key="i" class="review-star" src="/static/icons/star-yellow.svg" />
                  </view>
                </view>
              </view>
              <text class="review-content">{{ rv.content }}</text>
              <view v-if="rv.images && rv.images.length" class="review-images">
                <view v-for="(img, idx) in rv.images" :key="idx" class="review-image-wrapper">
                  <image class="review-image" :src="getImageUrl(img)" mode="aspectFill" />
                </view>
              </view>
            </view>
          </view>
        </CardSection>
        <view style="padding: 0 var(--spacing-md);">
          <AppButton text="写评价" icon="/static/icons/edit.svg" @click="goToReview" />
        </view>
      </template>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import { useDishStore } from '@/stores/dish'
import { useFavoriteStore } from '@/stores/favorite'
import { useUserStore } from '@/stores/user'
import AppButton from "@/components/AppButton.vue"
import { getImageUrl } from '@/utils/image'

const dishStore = useDishStore()
const favoriteStore = useFavoriteStore()
const userStore = useUserStore()

const dish = computed(() => dishStore.currentDish!)
const reviewList = computed(() => dishStore.reviewList)
const dishId = computed(() => dish.value?.id ?? 0)
const isFavorited = computed(() => favoriteStore.isFavorited(dishId.value))

function toggleFavorite() {
  if (!userStore.requireAuth()) return
  if (favoriteStore.isFavorited(dishId.value)) {
    favoriteStore.removeFavorite(dishId.value)
  } else {
    favoriteStore.addFavorite(dishId.value)
  }
}

function starCount(rating: number): number { return Math.round(rating) }

function goToReview() {
  if (!userStore.requireAuth()) return
  uni.navigateTo({ url: `/pages/pages-detail/review?dishId=${dishId.value}` })
}

function goToStall() {
  if (dish.value) {
    dishStore.navParams.stallName = dish.value.stallName
    dishStore.navParams.canteen = dish.value.canteen
    uni.navigateTo({ url: '/pages/pages-detail/stall' })
  }
}

function relativeTime(dateStr: string): string {
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diff = Math.floor((now - then) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return dateStr
}

let currentDishId = 0

async function loadDishData() {
  if (!currentDishId) return
  await Promise.all([
    dishStore.fetchDetail(currentDishId),
    dishStore.fetchReviews(currentDishId),
    favoriteStore.fetchFavorites(),
  ])
}

onLoad((query) => {
  if (query?.id) {
    currentDishId = Number(query.id)
    loadDishData()
  }
})

function onRefresh(e: any) {
  loadDishData().finally(() => { e.detail.complete() })
}
</script>

<style scoped>
.detail-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.title-row { display: flex; align-items: center; justify-content: space-between; }
.title-left { display: flex; align-items: center; gap: var(--spacing-sm); flex: 1; min-width: 0; }
.dish-name { font-size: var(--font-h2); font-weight: 600; color: var(--text-primary); flex-shrink: 0; }
.fav-btn { flex-shrink: 0; display: flex; align-items: center; padding: var(--spacing-xs); }
.fav-icon { width: 40rpx; height: 40rpx; display: block; }
.price-text { font-size: var(--font-subtitle); font-weight: 700; color: var(--color-price); flex-shrink: 0; }
.rating-row { display: flex; align-items: center; justify-content: space-between; padding-top: var(--spacing-sm); }
.rating-left { display: flex; align-items: center; gap: 6rpx; }
.star-icon-img { width: var(--icon-sm); height: var(--icon-sm); }
.rating-value { font-size: var(--font-body); font-weight: 500; color: var(--text-primary); }
.location-row { display: flex; align-items: center; gap: 6rpx; margin-top: var(--spacing-sm); padding-top: var(--spacing-sm); border-top: 2rpx solid var(--border-color); }
.location-icon-img { width: var(--icon-sm); height: var(--icon-sm); }
.location-text { font-size: var(--font-aux); color: var(--text-secondary); flex: 1; }
.arrow-right { height: var(--icon-lg); width: var(--icon-lg); }
.desc-content { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.5; display: block; }
.review-title { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); }
.review-list { margin-top: var(--spacing-sm); }
.review-item { padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); }
.review-item:last-child { border-bottom: none; }
.review-header { display: flex; gap: var(--spacing-sm); align-items: stretch; margin-bottom: var(--spacing-xs); }
.review-avatar { width: 64rpx; height: 64rpx; border-radius: 50%; flex-shrink: 0; background: var(--bg-page); }
.review-avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--border-color); }
.review-avatar-fallback { width: 32rpx; height: 32rpx; }
.review-header-right { flex: 1; display: flex; flex-direction: column; justify-content: space-between; min-height: 64rpx; }
.review-header-top { display: flex; align-items: center; justify-content: space-between; }
.review-name { font-size: var(--font-headline); font-weight: 500; color: var(--text-primary); }
.review-time { font-size: var(--font-aux); color: var(--text-tertiary); }
.review-stars { display: flex; align-items: center; gap: 10rpx; }
.review-star { width: 26rpx; height: 26rpx; }
.review-content { margin: var(--spacing-sm) 0; font-size: var(--font-body); color: var(--text-secondary); line-height: 1.4; display: block; }
.review-images { display: flex; flex-wrap: wrap; gap: 12rpx; }
.review-image-wrapper { width: 200rpx; height: 200rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); flex-shrink: 0; }
.review-image { width: 100%; height: 100%; display: block; }
</style>
