<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

/**
 * 通用弹窗（[UI 基线 §2.1 / §1.10](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * 形态判据（§1.10）：**≤4 个简单控件** → 本组件；**≥5 字段，或含图片 / 动态子表单 / 只读内容区块** → `BaseDrawer`。
 *
 * <p>交互口径与 `BaseDrawer` **一致**：遮罩点击 / `×` / **`ESC`** 均可关闭，
 * 打开期间**锁背景滚动**。
 */
const props = defineProps<{ title: string; open: boolean }>()
const emit = defineEmits<{ close: [] }>()

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') emit('close')
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      window.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
    } else {
      window.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="overlay" v-if="open" @click.self="emit('close')">
    <div class="modal-box" role="dialog" aria-modal="true">
      <div class="modal-head">
        <h3>{{ title }}</h3>
        <button class="modal-x" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </div>
      <div class="modal-body">
        <slot />
      </div>
      <div class="modal-actions" v-if="$slots.actions">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: var(--space-4);
}
.modal-box {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-pop);
  border: 1px solid var(--border-light);
  width: 100%;
  max-width: 520px;
  max-height: 88vh;
  overflow: auto;
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border-light);
}
.modal-head h3 {
  margin: 0;
  font-size: var(--font-lg);
}
.modal-x {
  border: none;
  background: none;
  font-size: 22px;
  color: var(--text-muted);
  cursor: pointer;
  line-height: 1;
}
.modal-body {
  padding: var(--space-5);
  /* 正文超长串（URL / 无空格长词）可断行，不撑破弹窗 */
  overflow-wrap: anywhere;
}
</style>
