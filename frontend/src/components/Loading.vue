<template>
  <view class="loading-state">
    <view class="loading-spinner" />
    <text class="loading-text">{{ text || '加载中...' }}</text>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  text?: string
}>(), {
  text: '加载中...',
})
</script>

<style scoped>
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl) var(--spacing-md);
}
.loading-spinner {
  width: 60rpx;
  height: 60rpx;
  border: 4rpx solid var(--border-color);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: var(--spacing-sm);
}
.loading-text {
  font-size: var(--font-body);
  color: var(--text-tertiary);
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
