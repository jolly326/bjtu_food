<template>
  <view class="empty-state">
    <IconSvg :name="icon || 'empty'" :size="120" color="var(--text-tertiary)" class="empty-icon" />
    <text class="empty-text">{{ text }}</text>
    <view v-if="retry" class="retry-btn" @tap="$emit('retry')">
      <text class="retry-text">重新加载</text>
    </view>
    <view v-else-if="actionText" class="action-btn" :class="{ pressed }" @touchstart="pressed = true" @touchend="pressed = false" @touchcancel="pressed = false" @mousedown="pressed = true" @mouseup="pressed = false" @mouseleave="pressed = false" @tap="$emit('action')">
      <IconSvg v-if="actionIcon" :name="actionIcon" :size="28" color="var(--text-white)" />
      <text class="action-text">{{ actionText }}</text>
    </view>
    <slot name="action" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import IconSvg from './IconSvg.vue'

defineProps<{
  /** 图标名（IconSvg 的 name，如 'comment'）；不传则用默认占位 */
  text?: string
  icon?: string
  retry?: boolean
  /** 操作按钮文案（如「发布第一条动态」）；与 retry 互斥 */
  actionText?: string
  /** 操作按钮左侧图标 */
  actionIcon?: string
}>()

defineEmits<{
  (e: 'retry'): void
  (e: 'action'): void
}>()

const pressed = ref(false)
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
.action-btn {
  margin-top: var(--spacing-md);
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm) var(--spacing-xl);
  border-radius: 32rpx;
  background: var(--color-primary);
  box-shadow: var(--shadow-card);
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.action-btn.pressed { transform: scale(var(--press-scale)); }
.action-text { font-size: 26rpx; color: var(--text-white); font-weight: 600; line-height: 1; }
</style>
