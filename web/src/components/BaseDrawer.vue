<script setup lang="ts">
/**
 * 处置 / 编辑抽屉（[UI 基线 §2.5](../../../docs/web/ui/公共组件与形态基线.md)）。
 *
 * <p>**载体判据**（基线 §1.10）：表单 **≤4 个简单控件** → `BaseModal`；
 * **≥5 个字段，或含图片 / 动态子表单 / 条件行 / 只读内容区块** → **本组件**。
 *
 * <p>规格：右侧滑入、宽 **640px**（条件构建器 720px）、圆角在**左**侧、毛玻璃底、
 * `--duration-drawer` + `--ease-drawer` 过渡；遮罩点击与 `×` 均可关闭。
 */
withDefaults(
  defineProps<{
    title: string
    open: boolean
    /** 抽屉宽度（默认 640px；条件构建器传 720px） */
    width?: string
  }>(),
  { width: '640px' },
)

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div class="drawer-overlay" v-if="open" @click.self="emit('close')">
    <aside class="drawer" :style="{ width }">
      <header class="drawer-head">
        <h3>{{ title }}</h3>
        <button class="drawer-x" type="button" aria-label="关闭" @click="emit('close')">×</button>
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
  background: var(--bg-card);
  border-top-left-radius: var(--radius-card);
  border-bottom-left-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  animation: drawer-in var(--duration-drawer, 0.24s) var(--ease-drawer, ease-out);
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
}
.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--border-light);
}
</style>
