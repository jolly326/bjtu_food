<template>
  <view class="status-badge" :class="status">
    <text>{{ statusText }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: 'pending' | 'approved' | 'rejected'
}>()

const statusText = computed(() => {
  const map: Record<string, string> = {
    pending: '审核中',
    approved: '已通过',
    rejected: '已拒绝',
  }
  return map[props.status] || props.status
})
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-icon);
  font-size: 22rpx;
  font-weight: 700;
}
.status-badge.pending {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}
.status-badge.approved {
  background: var(--color-success-soft);
  color: var(--color-success);
}
.status-badge.rejected {
  background: var(--color-error-soft);
  color: var(--color-error);
}
</style>
