<script setup lang="ts">
/**
 * Modal：唯一弹层基座（§4.2 自封装组件，§4.5 材质 + §4.4 动效；UI-02 收敛契约）。
 * 基于 Teleport 挂载到 body，含遮罩点击关闭、spring 入场/退场。
 * 调用方通过 show 控制显隐，close 事件关闭，默认插槽承载内容。
 * FormDialog / ConfirmDialog / 各页面弹层均复用此组件（ConfirmDialog 不再自带 Teleport 与样式副本）。
 *
 * UI-02 新增契约：
 *  - variant: 'dialog'（默认，带标题/关闭 X）| 'confirm'（role="alertdialog"、无右上角 X、默认聚焦「取消」）；
 *  - danger: 确认按钮走 btn-danger（破坏性操作二次确认）；
 *  - confirmText/cancelText/confirmLoading/onConfirm + slot footer + event confirm。
 *  - Web 弹层 220ms scale+opacity 过渡保留（spec §4.9 登记 Web 豁免）。
 */
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { Close } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    title?: string
    width?: number
    /** dialog=常规弹层；confirm=二次确认（无 X、alertdialog 语义、默认聚焦取消） */
    variant?: 'dialog' | 'confirm'
    /** 确认按钮是否为危险色（破坏性操作） */
    danger?: boolean
    confirmText?: string
    cancelText?: string
    confirmLoading?: boolean
    /** 异步确认函数：传入后确认按钮自动 loading 直至 resolve/reject */
    onConfirm?: () => void | Promise<void>
  }>(),
  { title: '', width: 520, variant: 'dialog', danger: false, confirmText: '确定', cancelText: '取消', confirmLoading: false },
)

const emit = defineEmits<{ close: []; confirm: [] }>()

const overlay = ref<HTMLElement | null>(null)
const box = ref<HTMLElement | null>(null)
const cancelBtn = ref<HTMLElement | null>(null)
const mounted = ref(false)
const visible = ref(false)
// 内部确认中（onConfirm 异步时自动管理）
const submitting = ref(false)
let hideTimer: number | undefined

/**
 * 退场卸载延时（ms）。必须与下方 .modal-box / .modal-overlay 的 CSS transition 时长一致
 * （0.22s = 220ms），否则会在退场动画结束前提前卸载 DOM，造成弹层闪烁。
 * （spec §4.9 登记 Web 弹层 220ms 豁免，保留）
 */
const EXIT_DURATION_MS = 220

// ESC 关闭（键盘可达性）
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.show) emit('close')
}

watch(
  () => props.show,
  (v) => {
    if (v) {
      // 快速关-开时清理未触发的退场卸载定时器，避免重开后 DOM 被 hideTimer 提前卸载（防闪烁）
      if (hideTimer) { window.clearTimeout(hideTimer); hideTimer = undefined }
      mounted.value = true
      window.addEventListener('keydown', onKeydown)
      requestAnimationFrame(() => {
        visible.value = true
        enterAnim()
      })
      // confirm 语义：默认聚焦「取消」，避免键盘误确认（§4.9 可达性）
      if (props.variant === 'confirm') {
        nextTick(() => cancelBtn.value?.focus())
      }
    } else {
      visible.value = false
      window.removeEventListener('keydown', onKeydown)
      // 退场（EXIT_DURATION_MS 与 CSS transition 一致）后卸载 DOM
      hideTimer = window.setTimeout(() => (mounted.value = false), EXIT_DURATION_MS)
    }
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (hideTimer) window.clearTimeout(hideTimer)
})

// 入场：blur + scale 同动（§4.5 实体化）
function enterAnim() {
  if (!box.value || !overlay.value) return
  overlay.value.style.opacity = '1'
  box.value.style.opacity = '1'
  box.value.style.transform = 'scale(1) translateY(0)'
}

async function handleConfirm() {
  if (submitting.value) return
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
  <Teleport to="body">
    <div
      v-if="mounted"
      ref="overlay"
      class="modal-overlay"
      :class="{ show: visible }"
      @click.self="emit('close')"
    >
      <div
        ref="box"
        class="modal-box"
        :class="{ show: visible, 'variant-confirm': variant === 'confirm' }"
        :style="{ width: width + 'px' }"
        :role="variant === 'confirm' ? 'alertdialog' : 'dialog'"
        aria-modal="true"
      >
        <header v-if="variant === 'dialog' && title" class="modal-header">
          <h3 class="modal-title">{{ title }}</h3>
          <button class="modal-close" v-press type="button" aria-label="关闭" @click="emit('close')">
            <el-icon><Close /></el-icon>
          </button>
        </header>
        <div class="modal-body">
          <slot />
          <slot name="footer" />
        </div>
        <!-- confirm 变体：底部取消/确认（取消默认聚焦；danger 时确认走危险色） -->
        <div v-if="variant === 'confirm'" class="modal-confirm-actions">
          <button ref="cancelBtn" class="btn-cancel" v-press type="button" @click="emit('close')">{{ cancelText }}</button>
          <button
            v-press type="button"
            :class="danger ? 'btn-danger' : 'btn-primary'"
            :disabled="submitting || confirmLoading"
            @click="handleConfirm"
          >
            {{ submitting || confirmLoading ? '处理中…' : confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--el-mask-color, rgba(0, 0, 0, 0.45));
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  opacity: 0;
  transition: opacity 0.2s var(--ease-out);
  backdrop-filter: var(--blur-material);
  -webkit-backdrop-filter: var(--blur-material);
  padding: var(--space-4);
}
.modal-overlay.show {
  opacity: 1;
}
.modal-box {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-pop);
  opacity: 0;
  transform: scale(0.96) translateY(8px);
  transform-origin: center;
  transition:
    opacity 0.22s var(--ease-out),
    transform 0.22s var(--ease-out);
  position: relative;
  overflow: hidden;
}
/* 品牌条：弹窗顶部主色细条（与页面头、登录卡一致） */
.modal-box::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--color-primary);
  z-index: 1;
}
.modal-box.show {
  opacity: 1;
  transform: scale(1) translateY(0);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-6) var(--space-3);
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}
.modal-title {
  margin: 0;
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.modal-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: var(--bg-soft);
  color: var(--text-secondary);
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.modal-close .el-icon { width: 16px; height: 16px; }
.modal-close:hover { background: var(--bg-hover); color: var(--text-primary); }
.modal-close:active { transform: scale(var(--press-scale)); }
.modal-close:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.modal-body {
  padding: var(--space-6);
  overflow-y: auto;
}
/* confirm 变体（UI-02 取值）：body padding = space-8 / space-8 / space-5，无 header */
.variant-confirm .modal-body {
  padding: var(--space-8) var(--space-8) var(--space-5);
}
/* confirm 变体底部操作区（原 ConfirmDialog.confirm-actions 收敛至此） */
.modal-confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: 0 var(--space-8) var(--space-5);
  flex-shrink: 0;
}

@media (prefers-reduced-motion: reduce) {
  .modal-overlay,
  .modal-box {
    transition: opacity 0.18s ease;
    transform: none !important;
  }
}
@media (prefers-reduced-transparency: reduce) {
  .modal-overlay {
    background: rgba(0, 0, 0, 0.55);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
