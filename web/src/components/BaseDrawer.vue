<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

/**
 * 处置 / 编辑抽屉（[UI 基线 §2.5](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p>**载体判据**（基线 §1.10）：表单 **≤4 个简单控件** → `BaseModal`；
 * **≥5 个字段，或含图片 / 动态子表单 / 条件行 / 只读内容区块** → **本组件**。
 *
 * <p>规格：右侧滑入、宽 **640px**（条件构建器 720px）、圆角在**左**侧、毛玻璃底、
 * `--duration-drawer` + `--ease-drawer` 过渡；遮罩点击 / `×` / `ESC` 均可关闭，
 * 表单**已修改**时先二次确认（`dirty`）。
 */
const props = withDefaults(
  defineProps<{
    title: string
    open: boolean
    /** 抽屉宽度（默认 640px；条件构建器传 720px） */
    width?: string
    /** 表单是否已修改：为真时关闭前二次确认「放弃未保存的修改？」 */
    dirty?: boolean
  }>(),
  { width: '640px', dirty: false },
)

const emit = defineEmits<{ close: [] }>()

/** 关闭入口统一收口：已修改 → 二次确认；确认放弃或未修改 → 交给父组件关闭 */
async function requestClose() {
  if (props.dirty) {
    try {
      await ElMessageBox.confirm('放弃未保存的修改？', '提示', {
        type: 'warning',
        confirmButtonText: '放弃',
        cancelButtonText: '继续编辑',
      })
    } catch {
      return
    }
  }
  emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') void requestClose()
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      window.addEventListener('keydown', onKeydown)
      // 与弹窗口径一致（基线 §2.1）：打开期间锁背景滚动
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
  <div class="drawer-overlay" v-if="open" @click.self="requestClose">
    <aside class="drawer" :style="{ width }">
      <header class="drawer-head">
        <h3>{{ title }}</h3>
        <button class="drawer-x" type="button" aria-label="关闭" @click="requestClose">×</button>
      </header>
      <div class="drawer-body">
        <slot />
      </div>
      <footer class="drawer-actions" v-if="$slots.actions">
        <slot name="actions" />
      </footer>
    </aside>
  </div>
</template>

<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}
.drawer {
  height: 100%;
  max-width: 100vw;
  /* 毛玻璃材质：半透卡底 + blur（基线 §2.5 / 设计变量 §2.6 --blur-material） */
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  backdrop-filter: var(--blur-material);
  -webkit-backdrop-filter: var(--blur-material);
  border-top-left-radius: var(--radius-card);
  border-bottom-left-radius: var(--radius-card);
  box-shadow: var(--shadow-pop);
  display: flex;
  flex-direction: column;
  animation: drawer-in var(--duration-drawer) var(--ease-drawer);
}
@keyframes drawer-in {
  from {
    transform: translateX(24px);
    opacity: 0.4;
  }
  to {
    transform: none;
    opacity: 1;
  }
}
.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border-light);
}
.drawer-head h3 {
  margin: 0;
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.drawer-x {
  border: none;
  background: none;
  font-size: 22px;
  line-height: 1;
  color: var(--text-muted);
  cursor: pointer;
}
.drawer-body {
  flex: 1;
  overflow: auto;
  padding: var(--space-5);
  /* 正文超长串（URL / 无空格长词）可断行，不撑破抽屉 */
  overflow-wrap: anywhere;
}
.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--border-light);
}
/* 减弱透明度：半透面转不透明（与 shared.css 的降级口径一致） */
@media (prefers-reduced-transparency: reduce) {
  .drawer {
    background: var(--bg-card);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
