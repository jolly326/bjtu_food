<template>
  <view class="empty-state">
    <IconSvg :name="icon || 'empty'" :size="120" color="var(--text-tertiary)" class="empty-icon" />
    <text class="empty-text">{{ text }}</text>
    <Pressable v-if="retry" class="retry-btn" :aria-label="'重新加载'" @tap="$emit('retry')">
      <text class="retry-text">重新加载</text>
    </Pressable>
    <Pressable v-else-if="actionText" class="action-btn" :aria-label="actionText" @tap="$emit('action')">
      <IconSvg v-if="actionIcon" :name="actionIcon" :size="28" color="var(--color-on-primary)" />
      <text class="action-text">{{ actionText }}</text>
    </Pressable>
    <slot name="action" />
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'
import Pressable from './Pressable.vue'

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
  font-size: var(--icon-4xl);
  line-height: 1;
  margin-bottom: var(--spacing-md);
  opacity: 0.4;
}
.empty-text {
  font-size: var(--font-body);
  color: var(--text-tertiary);
  font-weight: var(--weight-medium);
  text-align: center;
  line-height: 1.6;
}
.retry-btn {
  margin-top: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-xl);
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-btn);
}
.retry-text {
  font-size: var(--font-label);
  color: var(--text-secondary);
  font-weight: var(--weight-semibold);
}
.action-btn {
  margin-top: var(--spacing-md);
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm) var(--spacing-xl);
  border-radius: var(--radius-btn);
  background: var(--color-primary);
  box-shadow: var(--shadow-card);
  transition: transform var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.action-btn.pressed { transform: scale(var(--press-scale)); }
.action-text { font-size: var(--font-label); color: var(--color-on-primary); font-weight: var(--weight-semibold); line-height: 1; }
</style>
