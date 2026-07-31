<template>
  <view class="useful-btn" :class="{ active }" @tap="$emit('click')">
    <!-- 评价点赞（有用）：EMOJI.useful 占位（红线 §4.9③，区别于详情页底栏 EMOJI.like 喜欢） -->
    <text class="label">{{ EMOJI.useful }}</text>
    <text class="count">{{ count > 0 ? count : '' }}</text>
  </view>
</template>

<script setup lang="ts">
import { EMOJI } from '@/utils/emoji'

defineProps<{
  count: number
  active?: boolean
}>()

defineEmits<{
  (e: 'click'): void
}>()
</script>

<style scoped>
.useful-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-md);
  border: 2rpx solid var(--border-light);
  border-radius: var(--radius-tag);
  background: var(--bg-card);
  transition: transform 120ms var(--ease-out), background 120ms var(--ease-out), border-color 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.useful-btn:active {
  transform: scale(var(--press-scale));
}
.useful-btn.active {
  border-color: var(--color-like);
  background: var(--color-like-soft);
}
.label {
  font-size: 24rpx;
  color: var(--text-secondary);
}
.active .label {
  color: var(--color-like);
}
.count {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--text-secondary);
}
.active .count {
  color: var(--color-like);
}
</style>
