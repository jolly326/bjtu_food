<template>
  <view class="card-section">
    <text class="section-title" v-if="title">{{ title }}</text>
    <slot />
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
}>(), {
  title: '',
})
</script>

<style scoped>
.card-section {
  background: var(--bg-card);
  margin: var(--spacing-sm) 30rpx;
  padding: var(--spacing-md);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.card-section:first-of-type {
  margin-top: var(--spacing-md);
}
.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-sm);
  padding-bottom: var(--spacing-sm);
  border-bottom: 2rpx solid var(--border-color);
}
</style>
