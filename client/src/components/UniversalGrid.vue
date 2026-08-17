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
      <view class="u-icon">
        <IconSvg name="fire" :size="30" color="var(--color-cell-activity)" />
      </view>
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
      <view class="u-icon">
        <IconSvg name="edit" :size="30" color="var(--color-cell-feedback)" />
      </view>
      <view class="u-body">
        <text class="u-title">反馈菜品</text>
        <text class="u-sub">信息有误？帮我们纠错</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'

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
  min-height: 100rpx;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
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
.u-icon {
  flex-shrink: 0;
  width: 48rpx;
  height: 48rpx;
  border-radius: var(--radius-icon);
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 语义色图标软底（深浅模式通用：淡语义色底衬托深色图标，不引入白色块） */
.u-card-activity .u-icon {
  background: rgba(30, 95, 206, 0.1);
}
.u-card-feedback .u-icon {
  background: rgba(14, 158, 110, 0.1);
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
