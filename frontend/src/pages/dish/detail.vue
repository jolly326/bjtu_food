<template>
  <view class="page detail-page" v-if="dish">
    <Header title="菜品详情" showBack />

    <view class="dish-image">
      <ImageFallback :src="dish.image" placeholder="🍽️" />
    </view>

    <CardSection>
      <view class="title-row">
        <view class="title-left">
          <text class="dish-name">{{ dish.name }}</text>
          <TagLabel v-if="dish.tags.includes('必吃推荐')" text="必吃推荐" />
        </view>
        <text class="fav-btn" :class="{ favorited: isFavorited }" @tap="toggleFavorite">
          {{ isFavorited ? '❤️' : '🤍' }}
        </text>
      </view>

      <view class="rating-row">
        <view class="rating-left">
          <text class="star-icon">⭐</text>
          <text class="rating-value">{{ dish.rating }}</text>
          <text class="rating-count">({{ dish.ratingCount }}条评价)</text>
        </view>
        <text class="price-text">¥{{ dish.price }}</text>
      </view>

      <view class="location-row" @tap="goToStall">
        <text class="location-icon">📍</text>
        <text class="location-text">{{ dish.canteen }} · {{ dish.stallName }}</text>
        <text class="location-arrow">›</text>
      </view>
    </CardSection>

    <CardSection title="菜品介绍">
      <text class="desc-content">{{ dish.description }}</text>
    </CardSection>

    <CardSection>
      <view class="review-header-row">
        <text class="review-title">用户评价 ({{ reviewList.length }})</text>
        <view class="review-actions">
          <text class="write-review" @tap="goToReview">📝 写评价</text>
          <text class="action-sep">—</text>
          <text class="view-more" @tap="goToReviewList">查看更多 ›</text>
        </view>
      </view>

      <view class="review-list" v-if="reviewList.length > 0">
        <view v-for="rv in reviewList" :key="rv.id" class="review-item">
          <view class="review-top">
            <view class="review-user">
              <text class="review-name">{{ rv.userName }}</text>
              <text class="review-stars">{{ renderStars(rv.rating) }}</text>
            </view>
            <text class="review-time">{{ relativeTime(rv.createTime) }}</text>
          </view>
          <text class="review-content">{{ rv.content }}</text>
        </view>
      </view>
      <EmptyState v-else text="暂无评价" />
    </CardSection>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useDishStore } from '@/stores/dish'
import { useFavoriteStore } from '@/stores/favorite'
import { useUserStore } from '@/stores/user'
import type { DishDetail } from '@/types/dish'
import type { Review } from '@/types/review'

const dishStore = useDishStore()
const favoriteStore = useFavoriteStore()
const userStore = useUserStore()

const dish = computed(() => dishStore.currentDish as unknown as DishDetail)
const reviewList = computed(() => dishStore.reviewList as unknown as Review[])
const dishId = computed(() => dish.value?.id ?? 0)
const isFavorited = computed(() => favoriteStore.isFavorited(dishId.value))

function toggleFavorite() {
  if (!userStore.requireAuth()) return
  if (isFavorited.value) {
    favoriteStore.removeFavorite(dishId.value)
  } else {
    favoriteStore.addFavorite(dishId.value)
  }
}

function goToReview() {
  if (!userStore.requireAuth()) return
  uni.navigateTo({ url: `/pages/review/add?dishId=${dishId.value}` })
}

function goToReviewList() {
  uni.showToast({ title: '完整评价列表开发中', icon: 'none' })
}

function goToStall() {
  if (dish.value) {
    dishStore.navParams.stallName = dish.value.stallName
    dishStore.navParams.canteen = dish.value.canteen
    uni.navigateTo({ url: '/pages/stall/index' })
  }
}

function renderStars(rating: number): string {
  return '⭐'.repeat(Math.round(rating))
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

onLoad((query) => {
  if (query?.id) {
    const id = Number(query.id)
    dishStore.fetchDetail(id)
    dishStore.fetchReviews(id)
    favoriteStore.fetchFavorites()
  }
})
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: 40rpx;
}
.dish-image {
  width: 100%;
  height: 300rpx;
  background: var(--bg-page);
}

/* ===== 标题行 ===== */
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex: 1;
  min-width: 0;
}
.dish-name {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-primary);
  flex-shrink: 0;
}
.fav-btn {
  font-size: 44rpx;
  flex-shrink: 0;
  padding: 8rpx;
}
.fav-btn.favorited {
  color: var(--color-primary);
}

/* ===== 评分行 ===== */
.rating-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--spacing-sm);
}
.rating-left {
  display: flex;
  align-items: center;
  gap: 6rpx;
}
.star-icon {
  font-size: 28rpx;
}
.rating-value {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary);
}
.rating-count {
  font-size: 24rpx;
  color: var(--text-secondary);
}
.price-text {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-price);
}

/* ===== 位置信息 ===== */
.location-row {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-top: var(--spacing-sm);
  padding-top: var(--spacing-sm);
  border-top: 2rpx solid var(--border-color);
}
.location-icon {
  font-size: 32rpx;
}
.location-text {
  font-size: 26rpx;
  color: var(--text-secondary);
  flex: 1;
}
.location-arrow {
  font-size: 32rpx;
  color: var(--color-primary);
  font-weight: bold;
}

/* ===== 菜品介绍 ===== */
.desc-content {
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.5;
  display: block;
}

/* ===== 评价板块 ===== */
.review-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.review-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary);
}
.review-actions {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}
.write-review {
  font-size: 26rpx;
  color: var(--color-primary);
}
.action-sep {
  font-size: 24rpx;
  color: var(--text-tertiary);
}
.view-more {
  font-size: 26rpx;
  color: var(--text-secondary);
}

/* ===== 评价列表 ===== */
.review-list {
  margin-top: var(--spacing-sm);
}
.review-item {
  padding: var(--spacing-sm) 0;
  border-bottom: 2rpx solid var(--border-color);
}
.review-item:last-child {
  border-bottom: none;
}
.review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}
.review-user {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.review-name {
  font-size: var(--font-body);
  font-weight: 500;
  color: var(--text-primary);
}
.review-stars {
  font-size: 24rpx;
}
.review-time {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
.review-content {
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.4;
  display: block;
}
</style>
