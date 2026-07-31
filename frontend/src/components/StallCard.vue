<script lang="ts">
export interface DishPreview {
  id: number
  name: string
  price: number
  image: string
}

export interface StallInfo {
  id: number
  name: string
  location: string
  dishCount: number
  dishes: DishPreview[]
  image?: string
  rating?: number
  ratingCount?: number
}
</script>

<template>
  <!-- ========== 骨架屏：加载态 ========== -->
  <view v-if="loading" class="stall-card stall-skeleton">
    <view class="stall-header">
      <view class="skeleton-icon" />
      <view class="skeleton-info">
        <view class="skeleton-line skeleton-name" />
        <view class="skeleton-line skeleton-location" />
      </view>
    </view>
    <view class="stall-dishes-preview">
      <view v-for="i in 3" :key="i" class="skeleton-dish-item">
        <view class="skeleton-dish-img" />
        <view class="skeleton-dish-name" />
        <view class="skeleton-dish-price" />
      </view>
    </view>
  </view>

  <!-- ========== 正常卡片 ========== -->
  <view v-else class="stall-card" @tap="handleClick">
    <!-- 上半部分：档口信息行 -->
    <view class="stall-header">
      <!-- 左侧：档口图标 -->
      <view class="stall-icon-box">
        <image
          v-if="stall.image"
          :src="getImageUrl(stall.image)"
          mode="aspectFill"
          class="stall-icon-img"
        />
        <view v-else class="stall-icon-placeholder">
          <text class="stall-icon-fallback">{{ EMOJI.dishPlaceholder }}</text>
        </view>
      </view>

      <!-- 中间：档口详细信息 -->
      <view class="stall-info">
        <!-- 第一行：档口名 + 评分 -->
        <view class="stall-row-top">
          <text class="stall-name">{{ stall.name }}</text>
          <view v-if="stall.rating" class="stall-rating">
            <text class="stall-rating-star">{{ EMOJI.starFilled }}</text>
            <text class="stall-rating-value">{{ stall.rating.toFixed(1) }}</text>
          </view>
        </view>
        <!-- 第二行：位置 + 菜品数量 -->
        <view class="stall-row-bottom">
          <text class="stall-location-icon">{{ EMOJI.location }}</text>
          <text class="stall-location-text">{{ stall.location }}</text>
          <text class="stall-dish-dot">·</text>
          <text class="stall-dish-count">{{ stall.dishCount }}道菜</text>
        </view>
      </view>
    </view>

    <!-- 下半部分：菜品预览区 -->
    <view v-if="stall.dishes && stall.dishes.length > 0" class="stall-dishes-preview">
      <scroll-view
        class="dish-scroll"
        scroll-x
        enhanced
        show-scrollbar="false"
      >
          <view
            v-for="dish in stall.dishes.slice(0, 10)"
            :key="dish.id"
            class="dish-mini-card"
            @click.stop="goToDish(dish)"
          >
            <image
              v-if="dish.image"
              :src="getImageUrl(dish.image)"
              mode="aspectFill"
              class="dish-mini-img"
            />
          <view v-else class="dish-mini-img-placeholder">
              <text class="dish-mini-fallback">{{ EMOJI.dishPlaceholder }}</text>
          </view>
            <view class="dish-mini-info">
              <text class="dish-mini-name">{{ dish.name }}</text>
              <text class="dish-mini-price">￥{{ dish.price }}</text>
            </view>
          </view>
      </scroll-view>
    </view>

    <!-- 无菜品 -->
    <view v-else class="stall-empty-dishes">
      <text class="empty-text">暂无菜品</text>
    </view>

    <!-- 底部留白 -->
    <view class="stall-spacer" />
  </view>
</template>

<script setup lang="ts">
// StallInfo 已在上方 <script lang="ts"> 中导出，无需重复导入
import { getImageUrl } from '@/utils/image'
import { EMOJI } from '@/utils/emoji'

const _props = defineProps<{
  stall: StallInfo
  loading?: boolean
}>()

const emit = defineEmits<{
  click: [stall: StallInfo]
  dishClick: [dish: DishPreview]
}>()

function handleClick() {
  emit('click', _props.stall)
}

function goToDish(dish: DishPreview) {
  emit('dishClick', dish)
}
</script>

<style scoped>
/* ==================== 卡片容器 ==================== */
.stall-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--spacing-sm) var(--spacing-lg);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.stall-card:active { transform: scale(var(--press-scale)); }

/* ==================== 上半部分：档口信息行 ==================== */
.stall-header {
  display: flex;
  align-items: flex-start;
}

/* 左侧：档口图标 */
.stall-icon-box {
  width: 120rpx;
  height: 120rpx;
  border-radius: var(--radius-card);
  background: var(--bg-page);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stall-icon-img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-card);
}

.stall-icon-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stall-icon-fallback {
  font-size: 48rpx !important;
  line-height: 1 !important;
}

/* 中间：档口详细信息 */
.stall-info {
  flex: 1;
  min-width: 0;
  margin-left: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

/* 第一行：档口名 + 评分 */
.stall-row-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stall-name {
  font-size: var(--font-subtitle);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
  margin-right: var(--spacing-sm);
}

.stall-rating {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.stall-rating-star {
  font-size: 28rpx;
  line-height: 1;
  flex-shrink: 0;
  margin-right: var(--spacing-xs);
}

.stall-rating-value {
  font-size: var(--font-body);
  font-weight: 600;
  color: var(--color-star);
}

/* 第二行：位置图标 + 地址 + 数量 */
.stall-row-bottom {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.stall-location-icon {
  font-size: 24rpx;
  line-height: 1;
  flex-shrink: 0;
}

.stall-location-text {
  font-size: var(--font-body);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stall-dish-dot {
  font-size: var(--font-body);
  color: var(--border-bold);
  margin: 0 var(--spacing-xs);
}

.stall-dish-count {
  font-size: var(--font-body);
  color: var(--text-tertiary);
  flex-shrink: 0;
}

/* ==================== 下半部分：菜品预览区 ==================== */
.stall-dishes-preview {
  margin-top: var(--spacing-sm);
}

.dish-scroll {
  width: 100%;
  white-space: nowrap;
  font-size: 0;
}

/* 菜品小卡片 — 上：图片，下：名称+价格水平排布 */
.dish-mini-card {
  width: 260rpx;
  display: inline-flex;
  flex-direction: column;
  border-radius: var(--radius-icon);
  background: var(--bg-page);
  overflow: hidden;
  margin-right: var(--spacing-sm);
  vertical-align: top;
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.dish-mini-card:active {
  transform: scale(var(--press-scale));
}

.dish-mini-card:last-child {
  margin-right: 0;
}

.dish-mini-img {
  width: 100%;
  height: 180rpx;
  flex-shrink: 0;
  background: var(--border-color);
}

.dish-mini-img-placeholder {
  width: 100%;
  height: 180rpx;
  background: var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.dish-mini-fallback {
  font-size: 64rpx;
  line-height: 1;
}

.dish-mini-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-xs) var(--spacing-sm);
  gap: var(--spacing-xs);
}

.dish-mini-name {
  font-size: var(--font-body);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.dish-mini-price {
  font-size: var(--font-body);
  font-weight: 600;
  color: var(--color-price);
  flex-shrink: 0;
}

/* ==================== 无菜品状态 ==================== */
.stall-empty-dishes {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-md) 0;
  text-align: center;
}

.empty-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}

/* ==================== 骨架屏 ==================== */
.stall-skeleton {
  pointer-events: none;
}

.skeleton-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: var(--radius-card);
  background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  flex-shrink: 0;
}

.skeleton-info {
  flex: 1;
  margin-left: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--spacing-sm);
}

.skeleton-line {
  border-radius: 6rpx;
  background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-name {
  width: 55%;
  height: 32rpx;
}

.skeleton-location {
  width: 60%;
  height: 24rpx;
  margin-top: var(--spacing-sm);
}

.skeleton-dish-item {
  width: 200rpx;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  margin-right: var(--spacing-sm);
  vertical-align: top;
}

.skeleton-dish-img {
  width: 150rpx;
  height: 150rpx;
  border-radius: var(--radius-icon);
  background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-dish-name {
  width: 70%;
  height: 24rpx;
  border-radius: 6rpx;
  margin: var(--spacing-sm) auto 0;
  background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-dish-price {
  width: 40%;
  height: 22rpx;
  border-radius: 6rpx;
  margin: var(--spacing-xs) auto 0;
  background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

/* ==================== 动画 ==================== */
@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* ==================== 底部留白 ==================== */
.stall-spacer {
  height: 0;
}
</style>
