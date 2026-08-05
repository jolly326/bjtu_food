<template>
  <view class="section-title" :class="{ 'no-margin': noMargin }" @tap="$emit('tap')">
    <view class="section-bar" />
    <text class="section-text">{{ title }}</text>
    <slot name="extra" />
  </view>
</template>

<script setup lang="ts">
/**
 * 分区标题（全局统一组件，task-13 §0.3/§0.4）
 * 列表 / 分区标题统一左侧竖向 accent 条（品牌色），全端一致。
 * 首页食堂入口 / 热门菜品已有同款竖线，其余页面（find/community/profile/
 * canteen/stall/dish/review-list 等）的分区标题统一通过本组件补齐。
 */
withDefaults(defineProps<{
  /** 标题文案 */
  title: string
  /** 是否去掉左右外边距（用于已自带 padding 的容器内部） */
  noMargin?: boolean
}>(), {
  noMargin: false,
})

defineEmits<{
  /** 点击标题时触发（用于跳转到列表详情等） */
  (e: 'tap'): void
}>()
</script>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 0;
  margin-bottom: var(--spacing-sm);
  box-sizing: border-box;
}
/* 竖向 accent 条：4rpx × 28rpx 品牌色（§0.3） */
.section-bar {
  width: 8rpx;
  height: 32rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  flex-shrink: 0;
}
.section-text {
  /* Apple Design Typography：分区标题加大（h2 级）并加重（800），强化信息层级 */
  font-size: var(--font-h2);
  font-weight: var(--weight-heavy);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h2);
  flex: 1;
  min-width: 0;
}
</style>
