<script setup lang="ts">
/**
 * 空态（规格见 docs/ui/client/公共组件与形态基线.md §2.1.2）。
 * 与 RetryBlock 的分工：本组件 = 空态；RetryBlock = 失败态。
 */
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <view class="empty">
    <text class="empty__title">{{ title }}</text>
    <text v-if="subtitle" class="empty__subtitle">{{ subtitle }}</text>
    <view v-if="$slots.actions" class="empty__actions">
      <slot name="actions" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xl) 0;
}

.empty__title {
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-body);
}

.empty__subtitle {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

.empty__actions {
  display: flex;
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
}
</style>
