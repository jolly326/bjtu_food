<template>
  <view class="empty-state">
    <IconSvg v-if="icon" :name="icon" :size="120" color="var(--text-tertiary)" class="empty-icon" />
    <text v-else class="empty-icon">{{ EMOJI.empty }}</text>
    <text class="empty-text">{{ text }}</text>
    <view v-if="retry" class="retry-btn" @tap="$emit('retry')">
      <text class="retry-text">重新加载</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { EMOJI } from '@/utils/emoji'
import IconSvg from './IconSvg.vue'

defineProps<{
  /** 图标名（IconSvg 的 name，如 'comment'）；不传则用默认 emoji 占位 */
  text?: string
  icon?: string
  retry?: boolean
}>()

defineEmits<{
  (e: 'retry'): void
}>()
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl) var(--spacing-lg);
}
.empty-icon {
  font-size: 120rpx;
  line-height: 1;
  margin-bottom: var(--spacing-md);
  opacity: 0.4;
}
.empty-text {
  font-size: 28rpx;
  color: var(--text-tertiary);
  font-weight: 500;
  text-align: center;
  line-height: 1.6;
}
.retry-btn {
  margin-top: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-xl);
  border: 2rpx solid var(--border-color);
  border-radius: 32rpx;
}
.retry-text {
  font-size: 26rpx;
  color: var(--text-secondary);
  font-weight: 600;
}
</style>
