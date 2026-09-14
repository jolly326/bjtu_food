<script setup lang="ts">
/**
 * AccountView：学生账号管理。
 * 管理端已去登录与角色体系（2026-09-13 定型，改由环境变量口令保护），
 * 管理员账号模块随之下线，本页仅保留学生账号。
 */
import UserView from '@/views/user/UserView.vue'
</script>

<template>
  <div>
    <div class="block-title">学生账号</div>
    <UserView />
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
