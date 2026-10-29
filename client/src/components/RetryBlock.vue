<script setup lang="ts">
/**
 * 加载失败块（规格见 docs/ui/client/公共组件与形态基线.md §2.1.3）。
 *
 * 与「加载中」共用 `StatusBlock` 骨架 ⇒ 图标尺寸 / 文案字阶 / 居中位置完全一致；
 * 整块可点即重试，点击后父级立即切到加载态，本块随之卸载。
 */
import StatusBlock from './StatusBlock.vue'

/** 宿主节点虚拟化：组件根直接参与页面 flex 布局 */
defineOptions({ options: { virtualHost: true } })

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <StatusBlock
    name="warning"
    text="加载失败"
    aria-label="加载失败，点击重试"
    pressable
    @press="emit('retry')"
  />
</template>
