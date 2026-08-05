<script setup lang="ts">
/**
 * Modal：通用弹层（§4.2 自封装组件，§4.5 材质 + §4.4 动效）。
 * 基于 Teleport 挂载到 body，含遮罩点击关闭、spring 入场/退场。
 * 调用方通过 show 控制显隐，close 事件关闭，默认插槽承载内容。
 * FormDialog / 各页面弹层均复用此组件。
 */
import { ref, watch, onBeforeUnmount } from 'vue'
import { Close } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    title?: string
    width?: number
  }>(),
  { title: '', width: 520 },
)

const emit = defineEmits<{ close: [] }>()

const overlay = ref<HTMLElement | null>(null)
const box = ref<HTMLElement | null>(null)
const mounted = ref(false)
const visible = ref(false)

// ESC 关闭（键盘可达性）
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.show) emit('close')
}

watch(
  () => props.show,
  (v) => {
    if (v) {
      mounted.value = true
      window.addEventListener('keydown', onKeydown)
      requestAnimationFrame(() => {
        visible.value = true
        enterAnim()
      })
    } else {
      visible.value = false
      window.removeEventListener('keydown', onKeydown)
      // 退场（220ms 与 CSS transition 一致）后卸载 DOM
      window.setTimeout(() => (mounted.value = false), 220)
    }
  },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

// 入场：blur + scale 同动（§4.5 实体化）
function enterAnim() {
  if (!box.value || !overlay.value) return
  overlay.value.style.opacity = '1'
  box.value.style.opacity = '1'
  box.value.style.transform = 'scale(1) translateY(0)'
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
        :class="{ show: visible }"
        :style="{ width: width + 'px' }"
        role="dialog"
        aria-modal="true"
      >
        <header v-if="title" class="modal-header">
          <h3 class="modal-title">{{ title }}</h3>
          <button class="modal-close" v-press type="button" aria-label="关闭" @click="emit('close')">
            <el-icon><Close /></el-icon>
          </button>
        </header>
        <div class="modal-body">
          <slot />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
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
