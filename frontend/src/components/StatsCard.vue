<template>
  <view class="stats-card" :style="{ borderTopColor: color }">
    <text class="stats-value">{{ value }}</text>
    <text class="stats-label">{{ label }}</text>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: number
  color?: string
}>()
</script>

<style scoped>
.stats-card {
  flex: 1;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--spacing-md) var(--spacing-sm);
  text-align: center;
  border-top: 6rpx solid var(--color-primary);
  box-shadow: var(--shadow-card);
}
.stats-value {
  font-size: var(--font-h2);
  font-weight: 900;
  color: var(--text-primary);
  display: block;
}
.stats-label {
  font-size: var(--font-aux);
  font-weight: 600;
  color: var(--text-tertiary);
  margin-top: var(--spacing-xs);
  display: block;
}
</style>
