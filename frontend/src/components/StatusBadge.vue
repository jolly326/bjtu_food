<template>
  <view class="status-badge" :class="badgeClass">
    <text>{{ badgeText }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 审核状态模式（默认） */
  status?: 'pending' | 'approved' | 'rejected'
  /** 角色模式：与学生端角色徽标（STUDENT/ADMIN）二选一 */
  role?: 'student' | 'admin'
}>()

const badgeClass = computed(() => {
  if (props.role) return `role ${props.role}`
  return props.status || 'pending'
})

const badgeText = computed(() => {
  if (props.role) {
    const map: Record<string, string> = {
      student: '学生',
      admin: '管理员',
    }
    return map[props.role] || props.role
  }
  const map: Record<string, string> = {
    pending: '审核中',
    approved: '已通过',
    rejected: '已拒绝',
  }
  return map[props.status || 'pending'] || props.status
})
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
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
/* 角色徽标（学生端角色） */
.status-badge.role { background: var(--color-primary-soft); color: var(--color-primary); }
.status-badge.role.admin { background: var(--color-warning-soft); color: var(--color-warning); }
</style>
