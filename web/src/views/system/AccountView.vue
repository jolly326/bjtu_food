<script setup lang="ts">
/**
 * AccountView：账号（学生 / 管理员）。
 * 上下区块展示（无分段切换）；权限从简（WEB-100）：不再区分超管可见性，
 * 普通管理员同样可管理管理员账号，接口侧由后端 /admin/** 硬鉴权兜底。
 */
import UserView from '@/views/user/UserView.vue'
import AdminManageView from '@/views/admin/AdminManageView.vue'
</script>

<template>
  <div>
    <div class="block-title">学生账号</div>
    <UserView />
    <div class="block-title">管理员账号</div>
    <AdminManageView />
  </div>
</template>

<style scoped>
.block-title {
  display: flex;
  align-items: center;
  margin: var(--space-3) 0 var(--space-2);
  font-size: var(--font-sm);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  padding-left: var(--space-3);
  position: relative;
}
.block-title:first-child { margin-top: 0; }
.block-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  bottom: 3px;
  width: 3px;
  border-radius: var(--radius-xs);
  background: var(--color-primary);
}
</style>
