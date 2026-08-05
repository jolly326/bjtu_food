<script setup lang="ts">
/**
 * ConfirmDialog：二次确认弹窗（§4.2 自封装组件，破坏性操作必用）。
 * 复用全局 confirmStore，承载按钮即时反馈与弹层 spring 动效。
 */
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useConfirmStore } from '@/stores/confirmStore'

const confirm = useConfirmStore()
const overlay = ref<HTMLElement | null>(null)
const box = ref<HTMLElement | null>(null)
const show = ref(false)
const visibleState = ref(false)
// 破坏性确认弹窗：默认聚焦「取消」按钮，避免键盘误确认
const cancelBtn = ref<HTMLElement | null>(null)

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && confirm.visible) confirm.cancel()
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

watch(
  () => confirm.visible,
  (v) => {
    if (v) {
      show.value = true
      requestAnimationFrame(() => {
        visibleState.value = true
        enterAnim()
      })
      nextTick(() => cancelBtn.value?.focus())
    } else {
      visibleState.value = false
      // 退场后卸载
      window.setTimeout(() => (show.value = false), 220)
    }
  },
)

// 入场：blur + scale 同动（§4.5 实体化）
function enterAnim() {
  if (!box.value || !overlay.value) return
  overlay.value.style.opacity = '1'
  box.value.style.opacity = '1'
  box.value.style.transform = 'scale(1) translateY(0)'
}

function onCancel() {
  confirm.cancel()
}
function onOk() {
  confirm.ok()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="show" ref="overlay" class="confirm-overlay" :class="{ show: visibleState }" @click.self="onCancel">
      <div
        ref="box"
        class="confirm-box"
        :class="{ show: visibleState }"
        role="alertdialog"
        aria-modal="true"
      >
        <p class="confirm-msg">{{ confirm.message }}</p>
        <div class="confirm-actions">
          <button ref="cancelBtn" class="btn-cancel" v-press @click="onCancel">取消</button>
          <button class="btn-danger" v-press @click="onOk">确定</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.confirm-overlay {
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
}
.confirm-overlay.show {
  opacity: 1;
}
.confirm-box {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--space-8) var(--space-8) var(--space-5);
  width: 400px;
  max-width: 90vw;
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
/* 品牌条：与 Modal 弹窗一致的顶部主色细条 */
.confirm-box::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--color-primary);
}
.confirm-box.show {
  opacity: 1;
  transform: scale(1) translateY(0);
}
.confirm-msg {
  margin: 0 0 var(--space-6);
  font-size: var(--font-md);
  color: var(--text-primary);
  line-height: var(--leading-base);
}
.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}
.btn-cancel {
  padding: var(--space-2) var(--space-5);
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-size: var(--font-base);
  cursor: pointer;
}
.btn-danger {
  padding: var(--space-2) var(--space-5);
  border: 1px solid var(--color-error);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--color-error);
  font-size: var(--font-base);
  cursor: pointer;
}
.btn-danger:hover {
  background: var(--color-error);
  color: var(--text-white);
}

@media (prefers-reduced-motion: reduce) {
  .confirm-overlay,
  .confirm-box {
    transition: opacity 0.18s ease;
    transform: none !important;
  }
}
@media (prefers-reduced-transparency: reduce) {
  .confirm-overlay {
    background: rgba(0, 0, 0, 0.55);
    backdrop-filter: none;
  }
}
</style>
