<script setup lang="ts">
/**
 * 运营端线性图标：与学生端同源取形的 **Lucide 精选子集**（`assets/icons`，当前 10 枚；
 * 🔴「零消费即删」—— 新增前先全仓查引用，无消费的不入库，见 docs/ui/web/设计变量.md §2.4）。
 * SVG 的 `stroke="currentColor"` ⇒ 颜色继承所在元素的 `color`，无需为每个色调另备资源。
 * 图标与尺寸口径见 docs/ui/web/公共组件与形态基线.md。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 图标键（对应 `assets/icons/<name>.svg`，如 plus / pencil / trash-2） */
    name: string
    /** 边长（px） */
    size?: number
  }>(),
  { size: 16 },
)

const raw = import.meta.glob('../assets/icons/*.svg', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>

const svg = computed(() => raw[`../assets/icons/${props.name}.svg`] ?? '')

const box = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))
</script>

<template>
  <span class="app-icon" :style="box" v-html="svg" />
</template>

<style scoped>
.app-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  line-height: 0;
  color: inherit;
}

.app-icon :deep(svg) {
  width: 100%;
  height: 100%;
}
</style>
