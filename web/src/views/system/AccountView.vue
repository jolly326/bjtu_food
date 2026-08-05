<script setup lang="ts">
/**
 * AccountView：账号（学生 / 管理员）。
 * 上下区块展示（无分段切换）；管理员区块仅超管可见（后端已强校验）。
 */
import { computed } from 'vue'
import { useAdminUserStore } from '@/stores/adminUserStore'
import UserView from '@/views/user/UserView.vue'
import AdminManageView from '@/views/admin/AdminManageView.vue'

const adminUser = useAdminUserStore()
const isSuper = computed(() => adminUser.myRole === 'super_admin')
</script>

<template>
  <div>
    <div class="block-title">学生账号</div>
    <UserView />
    <template v-if="isSuper">
      <div class="block-title">管理员账号</div>
      <AdminManageView />
    </template>
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
  border-radius: 2px;
  background: var(--color-primary);
}
</style>
