<script setup lang="ts">
/** 列表加载失败：持久错误提示 + 重试（替代五份重复模板）。 */
defineProps<{ message: string }>()

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <div class="list-error">
    <span>{{ message }}</span>
    <button class="btn btn--sm" @click="emit('retry')">重试</button>
  </div>
</template>

<style scoped>
.list-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--el-border-radius-base);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-size: 14px;
}
</style>
