<script setup lang="ts">
/**
 * 运营端图标：**Streamline Plump Solid 实心 SVG**（`assets/icons`，13 枚，CC BY 4.0；
 * 🔴「零消费即删」—— 新增前先全仓查引用，无消费的不入库，口径见各页面稿）。
 * SVG 一律 `fill="currentColor"`（实心、挖孔靠 evenodd，无描边）⇒ 图标跟随宿主 `color`
 * （默认深棕 `#44382E`、弱化态 `#8C7B6B`、激活 / 主操作态暖橙 `#FF8A4C`）。
 *
 * 实现：Plump 资源以 `?raw` **eager 全量内联**（图标体积极小，13 枚合计 < 8KB），
 * 同步取值 ⇒ 首屏零闪烁、无按需 chunk、**渲染结果与资源文件绝对一致**
 * （渲染结果与资源文件同源）。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 图标键（对应 `assets/icons/<name>.svg`，如 add / exit / image） */
    name: string
    /** 边长（px） */
    size?: number
  }>(),
  { size: 16 },
)

/** 全部图标同步内联（Plump 资源本体，保留 fill="currentColor" 以跟随宿主 color） */
const ICONS = import.meta.glob('../assets/icons/*.svg', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

/** 未知键回退 help，避免空白 */
const FALLBACK_KEY = '../assets/icons/help.svg'

const svg = computed(() => {
  const raw = ICONS[`../assets/icons/${props.name}.svg`] ?? ICONS[FALLBACK_KEY] ?? ''
  return raw.trim()
})

const box = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))
</script>

<template>
  <span class="app-icon" :style="box" aria-hidden="true" v-html="svg" />
</template>

<style scoped>
.app-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  line-height: 0;
}

.app-icon :deep(svg) {
  width: 100%;
  height: 100%;
}
</style>
