<script setup lang="ts">
import { useToastStore } from '@/stores/toastStore'

const toast = useToastStore()

//全局消息提示组件，使用全局状态管理控制显示和内容，支持成功、错误和信息三种类型
const icons: Record<string, string> = {
  success: '✓',
  error: '✕',
  info: 'ℹ',
}
</script>

<template>
  <div class="toast-container">
    <TransitionGroup name="toast">
      <div v-for="m in toast.messages" :key="m.id" :class="['toast', m.type]">
        <span class="toast-icon">{{ icons[m.type] }}</span>
        <span class="toast-text">{{ m.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: var(--space-5);
  right: var(--space-5);
  z-index: 99999;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  pointer-events: none;
}
.toast {
  pointer-events: auto;
  padding: var(--space-3) var(--space-5);
  border-radius: var(--radius-md);
  font-size: var(--font-base);
  box-shadow: var(--shadow-pop);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 220px;
  backdrop-filter: var(--blur-material);
  -webkit-backdrop-filter: var(--blur-material);
}
.toast.success {
  background: var(--color-success-bg);
  color: var(--color-success);
  border: 1px solid color-mix(in srgb, var(--color-success) 35%, transparent);
}
.toast.error {
  background: var(--color-danger-soft);
  color: var(--color-error);
  border: 1px solid color-mix(in srgb, var(--color-error) 35%, transparent);
}
.toast.info {
  background: var(--color-primary-bg);
  color: var(--color-primary);
  border: 1px solid color-mix(in srgb, var(--color-primary) 35%, transparent);
}
.toast-icon {
  font-size: var(--font-xl);
  font-weight: var(--weight-bold);
}
.toast-text {
  flex: 1;
}

.toast-enter-active {
  transition: transform 0.3s var(--ease-out), opacity 0.3s var(--ease-out);
}
.toast-leave-active {
  transition: transform 0.25s var(--ease-out), opacity 0.25s var(--ease-out);
}
.toast-enter-from {
  transform: translateX(100%);
  opacity: 0;
}
.toast-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
