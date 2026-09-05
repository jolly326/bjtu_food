<template>
  <view class="state-view">
    <!-- 加载中：仅静态文本，任何情况下无过渡/动画（prefers-reduced-motion 同样静态） -->
    <view v-if="loading" class="state-loading" role="status" aria-label="加载中">
      <text class="state-loading-text">{{ loadingText }}</text>
    </view>

    <!-- 失败态：统一 EmptyState 默认占位图标 + 重试（ui-feed-loading：失败态不传语义错位 icon） -->
    <EmptyState
      v-else-if="failed"
      :text="errorText"
      :retry="true"
      @retry="$emit('retry')"
    />

    <!-- 空态：默认占位图标；可挂 action（如「发布第一条动态」）或 retry（如空结果重试） -->
    <EmptyState
      v-else-if="empty"
      :text="emptyText"
      :icon="emptyIcon"
      :retry="emptyRetry"
      :action-text="actionText"
      :action-icon="actionIcon"
      @retry="$emit('retry')"
      @action="$emit('action')"
    />
  </view>
</template>

<script setup lang="ts">
import EmptyState from './EmptyState.vue'

defineProps<{
  /** 是否处于加载中（最高优先级，覆盖失败/空态） */
  loading?: boolean
  /** 是否请求失败（展示错误态 + 重试） */
  failed?: boolean
  /** 是否无数据（展示空态） */
  empty?: boolean
  /** 加载文案 */
  loadingText?: string
  /** 失败态文案 */
  errorText?: string
  /** 空态文案 */
  emptyText?: string
  /** 空态图标（EmptyState 的 name；不传则用默认占位） */
  emptyIcon?: string
  /** 空态是否展示重试（与 action 互斥；失败态恒有重试） */
  emptyRetry?: boolean
  /** 空态操作按钮文案（如「发布第一条动态」） */
  actionText?: string
  /** 空态操作按钮图标 */
  actionIcon?: string
}>()

defineEmits<{
  (e: 'retry'): void
  (e: 'action'): void
}>()
</script>

<style scoped>
.state-view {
  display: flex;
  flex-direction: column;
}
.state-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl) var(--spacing-md);
  box-sizing: border-box;
}
.state-loading-text {
  font-size: var(--font-body);
  color: var(--text-tertiary);
}
/* 极简静态文本：减少动态效果偏好下同样静态 */
@media (prefers-reduced-motion: reduce) {
  .state-loading-text {
    transition: none;
  }
}
</style>
