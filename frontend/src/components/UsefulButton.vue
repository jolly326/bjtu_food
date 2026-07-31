<template>
  <view class="useful-btn" :class="{ active }" @tap="$emit('click')">
    <!-- 评价点赞（有用）：统一矢量图标 thumb（语义唯一：有用≠喜欢） -->
    <IconSvg class="label" name="thumb" :size="28" :color="active ? 'var(--color-like)' : 'var(--text-secondary)'" />
    <text class="count">{{ count > 0 ? count : '' }}</text>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'

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
