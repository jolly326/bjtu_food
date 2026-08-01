<template>
  <view
    class="review-item"
    :class="{ pressed: pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @longpress="onLongPress"
  >
    <!-- 头部：头像 + 昵称 + 时间 + 评分 -->
    <view class="review-header">
      <image v-if="review.userAvatar" class="review-avatar" :src="getImageUrl(review.userAvatar)" mode="aspectFill" />
      <view v-else class="review-avatar review-avatar-empty">
        <IconSvg name="user" :size="32" color="var(--text-tertiary)" />
      </view>
      <view class="review-header-right">
        <view class="review-header-top">
          <text class="review-name">{{ review.userNickname || '匿名用户' }}</text>
          <text class="review-time">{{ relativeTime(review.createTime) }}</text>
        </view>
        <Rating :model-value="review.rating" readonly :star-size="24" />
      </view>
    </view>

    <!-- 内容 -->
    <text class="review-content">{{ review.content }}</text>

    <!-- 图片（≤3，点击预览） -->
    <view v-if="review.images && review.images.length" class="review-images">
      <view v-for="(img, idx) in review.images" :key="idx" class="review-image-wrapper">
        <image class="review-image" :src="getImageUrl(img)" mode="aspectFill" @tap="previewImage(review.images!, idx)" />
      </view>
    </view>

    <!-- 底部互动：ic-heart 喜欢（乐观更新） -->
    <view class="review-actions">
      <view
        class="like-btn"
        :class="{ active: review.useful }"
        @tap.stop="toggleLike"
      >
        <IconSvg name="heart" :size="32" :color="review.useful ? 'var(--color-like)' : 'var(--text-tertiary)'" class="like-icon" />
        <text class="like-count" :class="{ active: review.useful }">{{ review.usefulCount && review.usefulCount > 0 ? review.usefulCount : '喜欢' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import Rating from '@/components/Rating.vue'
import { getImageUrl } from '@/utils/image'
import { toggleUseful } from '@/api/review'
import { useUserStore } from '@/stores/user'
import type { Review } from '@/types/review'

const props = defineProps<{
  review: Review
  /** 是否允许长按删除（仅本人评价时由父级开启） */
  deletable?: boolean
}>()

const emit = defineEmits<{ (e: 'delete', review: Review): void }>()

const userStore = useUserStore()
const pressed = ref(false)

function relativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diff = Math.floor((now - then) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return dateStr
}

function previewImage(images: string[], current: number) {
  uni.previewImage({ urls: images.map(getImageUrl), current: getImageUrl(images[current]) })
}

/** ic-heart 喜欢乐观更新（语义唯一：喜欢≠有用，与 moment 的 ic-thumb 区分） */
async function toggleLike() {
  if (!userStore.requireAuth()) return
  const rv = props.review
  const prevUseful = !!rv.useful
  const prevCount = rv.usefulCount || 0
  rv.useful = !prevUseful
  rv.usefulCount = prevUseful ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await toggleUseful(rv.id)
    rv.useful = res.useful
    rv.usefulCount = res.usefulCount
  } catch {
    rv.useful = prevUseful
    rv.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function onLongPress() {
  if (props.deletable) emit('delete', props.review)
}
</script>

<style scoped>
.review-item {
  padding: var(--spacing-sm) 0;
  border-bottom: 2rpx solid var(--border-color);
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.review-item:last-child { border-bottom: none; }
.review-item.pressed { transform: scale(var(--press-scale)); }
.review-header { display: flex; gap: var(--spacing-sm); align-items: stretch; margin-bottom: var(--spacing-xs); }
.review-avatar { width: 64rpx; height: 64rpx; border-radius: var(--radius-card); flex-shrink: 0; background: var(--bg-page); }
.review-avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.review-header-right { flex: 1; display: flex; flex-direction: column; justify-content: space-between; min-height: 64rpx; }
.review-header-top { display: flex; align-items: center; justify-content: space-between; }
.review-name { font-size: var(--font-headline); font-weight: 500; color: var(--text-primary); }
.review-time { font-size: var(--font-aux); color: var(--text-tertiary); }
.review-content { margin: var(--spacing-sm) 0; font-size: var(--font-body); color: var(--text-secondary); line-height: 1.4; display: block; }
.review-images { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.review-image-wrapper { width: 200rpx; height: 200rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); flex-shrink: 0; }
.review-image { width: 100%; height: 100%; display: block; }
.review-actions { margin-top: var(--spacing-xs); display: flex; justify-content: flex-end; }
.like-btn { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); border: 2rpx solid var(--border-light); transition: transform 0.12s ease, background 0.12s ease, border-color 0.12s ease; -webkit-tap-highlight-color: transparent; }
.like-btn:active { transform: scale(0.97); }
.like-btn.active { border-color: var(--color-like); background: var(--color-like-soft); }
.like-icon { line-height: 1; }
.like-count { font-size: 24rpx; font-weight: 600; color: var(--text-tertiary); }
.like-count.active { color: var(--color-like); }
</style>
