<script setup lang="ts">
/**
 * 空态块（**三页唯一实现**）：线性图标 + 衬线标题，仅此两项 —— 🔴 不带引导文案、不带按钮
 * （文本长度与简洁度对齐美味页；跳转类引导一律不做，切 tab 由用户自理）。
 * 规格见 docs/ui/client/公共组件与形态基线.md §2.1.2。
 */
import AppIcon from './AppIcon.vue'

defineOptions({ options: { virtualHost: true } })

defineProps<{
  /** 线性图标键（64rpx、muted、低透明度） */
  icon: string
  /** 衬线主文案 */
  title: string
}>()
</script>

<template>
  <view class="empty">
    <AppIcon class="empty__icon" :name="icon" :size="64" tone="muted" />
    <text class="empty__title">{{ title }}</text>
  </view>
</template>

<style lang="scss" scoped>
/* 占满可用高度并竖直居中：与失败态 RetryBlock 同一居中基准 */
.empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-height: 0;
  gap: var(--spacing-sm);
  padding: var(--spacing-xl) var(--page-gutter);
}

.empty__icon {
  margin-bottom: var(--spacing-xs);
  opacity: 0.55;
}

.empty__title {
  font-family: var(--font-serif);
  font-size: var(--font-h2);
  font-weight: var(--weight-regular);
  color: var(--text-title);
  text-align: center;
}
</style>
