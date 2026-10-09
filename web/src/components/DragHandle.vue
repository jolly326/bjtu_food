<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue'
/**
 * 拖拽手柄（[UI 基线 §2.5 #3](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p>**为什么单独成组件**：列表排序只允许**从手柄起拖**（整行 / 整卡 `draggable` 会与行内操作、
 * 文本选中争抢同一手势面，易误触）。手柄是唯一的 `draggable` 元素，行 / 卡退化为**放置目标**。
 *
 * <p>规格：六点手柄、字形尺寸 `--drag-handle-size`（唯一真源）、`--text-muted`、`cursor: grab`；
 * 热区由 `--space-1` 内距适度扩展（字形尺寸不变）。
 */
withDefaults(defineProps<{ label?: string }>(), { label: '拖拽排序' })

/** 起拖：由消费方接到 `useReorder` 的 `onDragStart(index)` */
const emit = defineEmits<{ dragstart: [] }>()
</script>

<template>
  <span
    class="drag-handle"
    draggable="true"
    role="button"
    tabindex="0"
    :aria-label="label"
    :title="label"
    @dragstart="emit('dragstart')"
  >
    <AppIcon name="drag" :size="14" color="var(--text-muted)" />
  </span>
</template>

<style scoped>
.drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--drag-handle-size);
  height: var(--drag-handle-size);
  padding: var(--space-1);
  color: var(--text-muted);
  cursor: grab;
  user-select: none;
}
.drag-handle:active {
  cursor: grabbing;
}
/* 字形尺寸与容器同源：同一个 `--drag-handle-size`（viewBox 坐标系固定 12×12） */
.drag-handle svg {
  fill: currentColor;
  display: block;
  width: var(--drag-handle-size);
  height: var(--drag-handle-size);
}
.drag-handle:focus-visible {
  outline: none;
  border-radius: var(--radius-xs);
  box-shadow: var(--focus-ring);
}
</style>
