<template>
  <view class="universal-grid">
    <!-- 万能区入口卡（活动模块暂缓接入，仅保留反馈菜品单卡） -->
    <view
      class="u-card u-card-feedback"
      role="button"
      aria-label="反馈菜品"
      hover-class="pressed"
      hover-stay-time="80"
      @tap="$emit('open-feedback')"
    >
      <view class="u-body">
        <text class="u-title">反馈菜品</text>
        <text class="u-sub">信息有误？帮我们纠错</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
defineEmits<{
  (e: 'open-feedback'): void
}>()
</script>

<style scoped>
.universal-grid {
  display: flex;
  gap: var(--spacing-md);
  box-sizing: border-box;
}
.u-card {
  flex: 1 1 0;
  min-width: 0;
  min-height: 104rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-sm);
  border-radius: var(--radius-card);
  border: 1rpx solid var(--border-card);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.u-card-feedback {
  background: var(--bg-cell-feedback);
  border-color: var(--border-cell-feedback);
}
.u-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}
.u-title {
  font-size: var(--font-card);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.u-sub {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
</style>
