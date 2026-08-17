<template>
  <view class="universal-grid">
    <!-- 最新活动 -->
    <view
      class="u-card u-card--activity"
      role="button"
      aria-label="最新活动"
      hover-class="pressed"
      hover-stay-time="80"
      @tap="$emit('open-activity')"
    >
      <view class="u-icon u-icon--activity">
        <IconSvg name="fire" :size="30" color="var(--color-cell-activity)" />
      </view>
      <view class="u-body">
        <text class="u-title">最新活动</text>
        <text class="u-sub">{{ activityTitle || '敬请期待' }}</text>
      </view>
    </view>

    <!-- 反馈菜品 -->
    <view
      class="u-card u-card--feedback"
      role="button"
      aria-label="反馈菜品"
      hover-class="pressed"
      hover-stay-time="80"
      @tap="$emit('open-feedback')"
    >
      <view class="u-icon u-icon--feedback">
        <IconSvg name="edit" :size="30" color="var(--color-cell-feedback)" />
      </view>
      <view class="u-body">
        <text class="u-title">反馈菜品</text>
        <text class="u-sub">纠错 · 建议 · 帮助我们更好</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'

defineProps<{
  /** 最新活动标题（已有活动时作为副文案预览） */
  activityTitle?: string
}>()

defineEmits<{
  (e: 'open-activity'): void
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
  min-height: 100rpx;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm);
  border-radius: var(--radius-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
/* 两卡独立浅底区分模块（蓝/绿），配色走 token，深色模式由 .theme-dark 覆盖 */
.u-card--activity { background: var(--bg-cell-activity); }
.u-card--activity .u-title { color: var(--color-cell-activity); }
.u-card--feedback { background: var(--bg-cell-feedback); }
.u-card--feedback .u-title { color: var(--color-cell-feedback); }

.u-icon {
  flex-shrink: 0;
  width: 48rpx;
  height: 48rpx;
  border-radius: var(--radius-icon);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}
.u-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.u-title {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.u-sub {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
