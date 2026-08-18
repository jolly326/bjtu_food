<template>
  <view class="universal-grid">
    <!-- 双卡（最新活动 + 反馈菜品）：活动入口常驻，点击进入活动列表页；
         有活动标题展示摘要，无活动时兜底文案避免空洞可点项 -->
    <view
      class="u-card u-card-activity"
      role="button"
      aria-label="最新活动"
      hover-class="pressed"
      hover-stay-time="80"
      @tap="$emit('open-activity')"
    >
      <view class="u-body">
        <text class="u-title">最新活动</text>
        <text class="u-sub">{{ activityTitle || '看看最近有哪些活动' }}</text>
      </view>
    </view>

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
defineProps<{
  /** 最新活动标题（已有活动时展示活动+反馈双卡；无活动仅反馈整行入口） */
  activityTitle?: string
}>()

defineEmits<{
  (e: 'open-activity'): void
  (e: 'open-feedback'): void
}>()
</script>

<style scoped>
/* 双卡布局（有活动时两列；无活动时单行入口不占两卡宽度） */
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
/* 万能区语义卡：与页面白色组件（广播条/菜品卡）区分 —— 活动=冷蓝、反馈=青绿 */
.u-card-activity {
  background: var(--bg-cell-activity);
  border-color: var(--border-cell-activity);
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
