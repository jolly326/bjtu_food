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
        <IconSvg name="fire" :size="30" color="#2F6FED" />
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
        <IconSvg name="edit" :size="30" color="#12B886" />
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
  flex: 1 1 0;
  min-width: 0;
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
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 两卡配色：原理品牌色（朱砂红），采用冷调蓝 / 青绿与品牌红形成 Hue 对比，提升页面活跃度 */
.u-card--activity { background: #E8F1FB; }
.u-card--activity .u-title { color: #2F6FED; }
.u-card--activity .u-more { color: #2F6FED; }

.u-card--feedback { background: #E7F7F2; }
.u-card--feedback .u-title { color: #12B886; }
.u-card--feedback .u-more { color: #12B886; }

/* 深色模式：对应冷色深底，保证文字对比 ≥4.5:1 */
.theme-dark .u-card--activity { background: #16263A; }
.theme-dark .u-card--feedback { background: #0F2E26; }
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
