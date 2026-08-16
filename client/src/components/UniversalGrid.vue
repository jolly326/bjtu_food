<template>
  <view class="universal-grid">
    <!-- 最新活动 -->
    <view
      class="u-card u-card--activity"
      role="button"
      aria-label="最新活动"
      @tap="$emit('open-activity')"
    >
      <view class="u-icon u-icon--activity">
        <IconSvg name="fire" :size="30" color="#FFFFFF" />
      </view>
      <view class="u-body">
        <text class="u-title">最新活动</text>
        <text class="u-sub">{{ hasActivity ? '查看最新校园美食活动' : '敬请期待' }}</text>
      </view>
      <text class="u-more">›</text>
    </view>

    <!-- 反馈菜品 -->
    <view
      class="u-card u-card--feedback"
      role="button"
      aria-label="反馈菜品"
      @tap="$emit('open-feedback')"
    >
      <view class="u-icon u-icon--feedback">
        <IconSvg name="edit" :size="30" color="#FFFFFF" />
      </view>
      <view class="u-body">
        <text class="u-title">反馈菜品</text>
        <text class="u-sub">纠错 · 建议 · 帮助我们更好</text>
      </view>
      <text class="u-more">›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'

defineProps<{
  /** 是否已有活动（控制副文案） */
  hasActivity?: boolean
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
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}
.u-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 10rpx var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  transition: transform 0.12s ease;
}
.u-card:active { transform: scale(var(--press-scale)); }
.u-icon {
  flex-shrink: 0;
  width: 48rpx;
  height: 48rpx;
  border-radius: var(--radius-card);
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 两卡独立配色：刻意避开主题红，采用蓝 / 粉，色相不相近、辨识度高 */
.u-card--activity { background: #E8F1FB; }
.u-card--activity .u-title { color: #2F6FED; }
.u-card--activity .u-more { color: #2F6FED; }
.u-card--activity .u-icon { background: #2F6FED; }

.u-card--feedback { background: #FCE8F1; }
.u-card--feedback .u-title { color: #E85D9C; }
.u-card--feedback .u-more { color: #E85D9C; }
.u-card--feedback .u-icon { background: #E85D9C; }

/* 深色模式：同色系深色底，保持可读 */
.theme-dark .u-card--activity { background: #1B2A40; }
.theme-dark .u-card--feedback { background: #3A2233; }
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
.u-more {
  flex-shrink: 0;
  font-size: 40rpx;
  line-height: 1;
  color: var(--text-tertiary);
}
</style>
