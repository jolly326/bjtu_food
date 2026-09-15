<script setup lang="ts">
/**
 * AccountView：学生账号管理。
 * 管理端已去登录与角色体系（2026-09-13 定型，改由环境变量口令保护），
 * 管理员账号模块随之下线，本页仅保留学生账号。
 * 2026-09-15（本轮）：删除页内「学生账号」标题——与上方「账号」分类卡重复（视觉噪音）。
 */
import UserView from '@/views/user/UserView.vue'
</script>

<template>
  <UserView />
</template>
