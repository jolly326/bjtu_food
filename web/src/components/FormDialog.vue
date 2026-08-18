<script setup lang="ts">
/**
 * FormDialog：表单弹窗（§4.2 自封装组件）。
 * 基于 Modal（已含 §4.5 材质与 §4.4 动效），提供统一标题 + 内容槽 + 底部操作槽。
 * 调用方通过 show 控制显隐，cancel 事件关闭。
 */
import { ref } from 'vue'
import Modal from './Modal.vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    title?: string
    width?: number
    /** 是否显示默认底部取消/保存按钮，false 时由调用方用 #actions 槽自定义 */
    footer?: boolean
    confirmText?: string
    cancelText?: string
    confirmLoading?: boolean
    confirmDisabled?: boolean
    /** 异步提交函数：传入后按钮自动进入 loading 直至 resolve/reject */
    onConfirm?: () => void | Promise<void>
  }>(),
  { footer: true, confirmText: '保存', cancelText: '取消', confirmLoading: false, confirmDisabled: false },
)

const emit = defineEmits<{ close: []; confirm: [] }>()

// 内部提交中（onConfirm 异步时自动管理）
const submitting = ref(false)

async function handleConfirm() {
  if (submitting.value || props.confirmDisabled) return
  if (props.onConfirm) {
    submitting.value = true
    try {
      await props.onConfirm()
    } finally {
      submitting.value = false
    }
  } else {
    emit('confirm')
  }
}
</script>

<template>
  <Modal :show="show" :title="title" :width="width" @close="emit('close')">
    <slot />
    <template v-if="footer">
      <div class="modal-actions">
        <button class="btn-cancel" v-press @click="emit('close')">{{ cancelText }}</button>
        <button class="btn-primary" v-press :disabled="confirmDisabled || submitting || confirmLoading" @click="handleConfirm">
          {{ submitting || confirmLoading ? '处理中…' : confirmText }}
        </button>
      </div>
    </template>
    <template v-else>
      <div class="modal-actions">
        <slot name="actions" />
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-light);
}
/* 按钮（btn-primary/btn-cancel）走 shared.css 全局基线，此处不重复覆盖 */
</style>
