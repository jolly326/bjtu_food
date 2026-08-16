<template>
  <view class="universal-grid">
    <!-- 最新活动 -->
    <view
      class="u-card"
      role="button"
      aria-label="最新活动"
      @tap="$emit('open-activity')"
    >
      <view class="u-icon u-icon--activity">
        <IconSvg name="fire" :size="40" color="var(--color-primary)" />
      </view>
      <view class="u-body">
        <text class="u-title">最新活动</text>
        <text class="u-sub">{{ hasActivity ? '查看最新校园美食活动' : '敬请期待' }}</text>
      </view>
      <text class="u-more">›</text>
    </view>

    <!-- 全部菜品 -->
    <view
      class="u-card"
      role="button"
      aria-label="全部菜品"
      @tap="$emit('open-find')"
    >
      <view class="u-icon u-icon--find">
        <IconSvg name="dish" :size="40" color="var(--color-primary)" />
      </view>
      <view class="u-body">
        <text class="u-title">全部菜品</text>
        <text class="u-sub">分类 · 榜单 · 发现更多</text>
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
  (e: 'open-find'): void
}>()
</script>

<style scoped>
.universal-grid {
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-md) 0;
  box-sizing: border-box;
}
.u-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
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
  width: 80rpx;
  height: 80rpx;
  border-radius: var(--radius-card);
  background: var(--bg-soft);
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
.u-more {
  flex-shrink: 0;
  font-size: 40rpx;
  line-height: 1;
  color: var(--text-tertiary);
}
</style>
