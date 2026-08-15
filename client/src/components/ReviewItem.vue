<template>
  <view class="review-item" :class="{ pressed: pressedKey === review.id }">
    <image v-if="avatarOk && review.userAvatar" class="review-avatar" :src="review.userAvatar" mode="aspectFill" @error="avatarOk = false" />
    <view v-else class="review-avatar review-avatar-empty">
      <IconSvg name="user" :size="36" color="var(--text-tertiary)" />
    </view>
    <view class="review-body">
      <view class="review-head">
        <text class="review-nickname">{{ review.userNickname || '匿名用户' }}</text>
        <view class="review-rating">
          <IconSvg
            v-for="i in 5"
            :key="i"
            name="star-filled"
            :size="22"
            :color="i <= (review.rating || 0) ? 'var(--color-star)' : 'var(--border-color)'"
            class="review-star"
          />
        </view>
        <text class="review-time">{{ relativeTime(review.createTime) }}</text>
      </view>
      <text class="review-content">{{ review.content }}</text>
      <view class="review-foot" v-if="review.images && review.images.length">
        <image
          v-for="(img, idx) in review.images.slice(0, 3)"
          :key="idx"
          class="review-thumb"
          :src="img"
          mode="aspectFill"
          @tap="previewImage(idx)"
        />
      </view>
      <view class="review-actions" v-if="!hideUseful">
        <view class="review-like" :class="{ active: usefulActive }" @tap.stop="$emit('like', review)">
          <IconSvg
            :name="usefulActive ? 'heart-filled' : 'heart'"
            :size="28"
            :color="usefulActive ? 'var(--color-primary)' : 'var(--text-tertiary)'"
          />
          <text class="review-like-text" :class="{ active: usefulActive }">{{ likeLabel }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import type { Review } from '@/types/review'

const props = defineProps<{
  review: Review
  /** 当前用户是否已点赞（控制胶囊按钮填充态） */
  usefulActive?: boolean
  /** 隐藏点赞（喜欢）操作：档口详情页评价区按产品决策不设点赞 */
  hideUseful?: boolean
}>()

defineEmits<{
  (e: 'like', review: Review): void
}>()

const pressedKey = ref<number | ''>('')
const avatarOk = ref(true)

const likeLabel = computed(() => {
  const n = (props.review.usefulCount || 0) + (props.usefulActive ? 1 : 0)
  return n > 0 ? `喜欢 ${n}` : '喜欢'
})

function previewImage(idx: number) {
  // 微信原生预览接口
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ;(uni as any).previewImage({ urls: props.review.images, current: props.review.images[idx] })
}

function relativeTime(t?: string) {
  if (!t) return ''
  const ms = Date.now() - new Date(t).getTime()
  const m = Math.floor(ms / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d} 天前`
  return new Date(t).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
/* ===== 评价项（Apple Design Craft + 设计系统 card 规范，2026-08-04 打磨）。
   三处评论区共用（动态详情 / 菜品详情 / 档口详情），打磨一处即统一全部。
   设计要点：hairline 分隔、touch 反馈、字号层级、头像圆角正方形、星级 token、喜欢胶囊按钮 */
.review-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) 0;
  border-bottom: 1rpx solid var(--border-color);
  -webkit-tap-highlight-color: transparent;
  /* 去 300ms tap 延迟（UI Pro Max touch guideline） */
  touch-action: manipulation;
  transition: background-color 120ms var(--ease-out);
}
.review-item:last-child { border-bottom: none; }
.review-item.pressed { background-color: var(--bg-soft); }

/* 头像：圆角正方形（16rpx，与头像设计一致） */
.review-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  background: var(--bg-page);
  flex-shrink: 0;
}
.review-avatar-empty { display: flex; align-items: center; justify-content: center; }

/* 右侧内容 */
.review-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

/* 头部：昵称 + 星级 + 时间（三段同行，视觉层级清晰） */
.review-head {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: nowrap;
}
.review-nickname {
  font-size: var(--font-card);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  flex-shrink: 0;
}
.review-rating { display: inline-flex; align-items: center; gap: 2rpx; }
.review-star { display: inline-block; }
.review-time {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  margin-left: auto;
  flex-shrink: 0;
}

/* 正文：可多行（之前单行截断体验差） */
.review-content {
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

/* 缩略图 */
.review-foot { display: flex; gap: var(--spacing-xs); margin-top: var(--spacing-xs); }
.review-thumb {
  width: 144rpx;
  height: 144rpx;
  border-radius: var(--radius-card);
  background: var(--bg-page);
}

/* 操作栏：喜欢胶囊按钮（未点赞灰底 + 已点赞主色软底 + 心形填充） */
.review-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-xs);
}
.review-like {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius-tag);
  border: 1rpx solid var(--border-color);
  background: var(--bg-card);
  transition: transform 120ms var(--ease-out), background-color 120ms ease, border-color 120ms ease;
}
.review-like:active { transform: scale(var(--press-scale)); }
.review-like.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}
.review-like-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-weight: var(--weight-semibold);
}
.review-like-text.active { color: var(--color-primary); }
</style>