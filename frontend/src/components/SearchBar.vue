<template>
  <view class="search-bar" :style="customStyle" @tap="handleTap">
    <view class="search-icon">
      <image src="/static/icons/search.svg" class="icon-img" />
    </view>
    <text class="search-placeholder">{{ placeholder }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  placeholder?: string
  margin?: string
}>(), {
  placeholder: '搜索菜品或档口...',
  margin: 'var(--spacing-md)',
})

const emit = defineEmits<{
  tap: []
}>()

const customStyle = computed(() => ({
  margin: props.margin,
}))

function handleTap() {
  emit('tap')
}
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border-radius: var(--radius-btn);
  padding: 20rpx var(--spacing-lg);
  border: 2rpx solid var(--border-color);
}
.search-icon {
  display: flex;
  align-items: center;
}
.icon-img {
  width: 32rpx;
  height: 32rpx;
  margin-right: 20rpx;
}
.search-placeholder {
  font-size: 28rpx;
  color: var(--text-tertiary);
  flex: 1;
}
</style>
