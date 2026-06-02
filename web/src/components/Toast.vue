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
  top: 20px;
  right: 20px;
  z-index: 99999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}
.toast {
  pointer-events: auto;
  padding: 12px 20px;
  border-radius: 8px;
  font-size: 14px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 220px;
  backdrop-filter: blur(4px);
}
.toast.success {
  background: #f6ffed;
  color: #52c41a;
  border: 1px solid #b7eb8f;
}
.toast.error {
  background: #fff2f0;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
}
.toast.info {
  background: #e6f7ff;
  color: #1890ff;
  border: 1px solid #91d5ff;
}
.toast-icon {
  font-size: 16px;
  font-weight: 700;
}
.toast-text {
  flex: 1;
}

.toast-enter-active {
  transition: all 0.3s ease;
}
.toast-leave-active {
  transition: all 0.25s ease;
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
