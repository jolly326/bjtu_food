<template>
  <view class="empty-state">
    <text class="empty-icon" v-if="icon">{{ icon }}</text>
    <text class="empty-text">{{ text }}</text>
    <text class="empty-hint" v-if="hint">{{ hint }}</text>
    <view v-if="$slots.action">
      <slot name="action" />
    </view>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  icon?: string
  text: string
  hint?: string
}>(), {
  icon: '',
  hint: '',
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 30rpx;
}
.empty-icon {
  font-size: 80rpx;
  margin-bottom: 20rpx;
}
.empty-text {
  font-size: var(--font-body);
  color: var(--text-secondary);
  margin-bottom: 8rpx;
}
.empty-hint {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  margin-bottom: 40rpx;
}
</style>
