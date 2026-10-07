<script setup lang="ts">
import { computed } from 'vue'

/**
 * AppIcon —— 管理端唯一矢量图标组件（**与学生端 `client/src/components/AppIcon.vue` 同源同款**）。
 *
 * <p>图标来源与许可证：IconPark（https://iconpark.oceanengine.com ，字节跳动开源图标库），
 * **Apache License 2.0**（可商用）。主题 = outline；几何为官方产物中的路径数据。
 * 来源登记见 docs/ui/web/公共组件与形态基线.md §1.14。
 *
 * <p>**两端统一的到底是什么**：图形**风格与网格**（IconPark outline、48 网格、`stroke-width 2`、
 * 圆角端点）与**档位语义**（同一档位在两端表示同一量级）—— 两端运行环境不同（小程序 vs 浏览器），
 * 故各持一份同源几何，而非共享构建产物：小程序端无原生 `<svg>`，走 `<image>` + data-uri；
 * 本端有原生 `<svg>`，直接渲染。
 *
 * <p>**用法**：`<AppIcon name="image" :size="12" :color="COLOR" />`
 *  - `size`：数字 = **px**，且只允许取 §1.14 档位表内的档位；
 *  - `color`：**必须传实色**（语义色变量值或 `#RRGGBB`）—— 继承到的 `currentColor` 语义由本组件
 *    显式落到 `--text-muted`（见 {@link stroke}）。
 *
 * **零消费的图标键不登记**：新增图标时连同消费点一起登记进 §1.14 的键表。
 */

/** 单个图标的官方几何（路径；`fill: true` = 同套几何的主轮廓填色变体） */
interface IconGeometry {
  path: string[]
  fill?: boolean
}

/** 配图语义（`image`）：既是业务键，也是未知图标名的回退目标 */
const IMAGE: IconGeometry = {
  path: [
    'M5 10C5 8.89543 5.89543 8 7 8L41 8C42.1046 8 43 8.89543 43 10V38C43 39.1046 42.1046 40 41 40H7C5.89543 40 5 39.1046 5 38V10Z',
    'M14.5 18C15.3284 18 16 17.3284 16 16.5C16 15.6716 15.3284 15 14.5 15C13.6716 15 13 15.6716 13 16.5C13 17.3284 13.6716 18 14.5 18Z',
    'M15 24L20 28L26 21L43 34V38C43 39.1046 42.1046 40 41 40H7C5.89543 40 5 39.1046 5 38V34L15 24Z',
  ],
}

/** 图标键 → 官方几何（唯一真源，与学生端 `AppIcon.vue` 的 `ICONS` 同源） */
const ICONS: Record<string, IconGeometry> = { image: IMAGE }

const props = withDefaults(
  defineProps<{
    /** 图标名（见 ICONS 键） */
    name: string
    /** 尺寸档位（数字 = px；允许档位见 docs/ui/web/公共组件与形态基线.md §1.14） */
    size?: number
    /** 描边色（实色）；省略时落 `--text-muted` */
    color?: string
  }>(),
  { size: 14, color: 'var(--text-muted)' },
)

/** 未知图标名回退到 `image`（DEV 告警，生产静默）；`ICONS` 的非空由 `image` 键恒成立保证 */
const icon = computed<IconGeometry>(() => ICONS[props.name] ?? IMAGE)

if (import.meta.env?.DEV && !ICONS[props.name]) {
  console.warn('[AppIcon] unknown icon name:', props.name)
}

/**
 * SVG 内部片段。
 * ⚠️ 由本文件常量几何拼出（**不含任何外部输入**），故 `v-html` 无注入面；
 * Vue 对 `<svg>` 的 `v-html` 走 innerHTML，可正常承载 `<path>`。
 */
const inner = computed(() => {
  const g = icon.value
  const paint = g.fill
    ? `fill="${props.color}" stroke="none"`
    : `fill="none" stroke="${props.color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"`
  return g.path.map((d) => `<path d="${d}" ${paint}/>`).join('')
})
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 48 48"
    aria-hidden="true"
    focusable="false"
    v-html="inner"
  />
</template>

<style scoped>
.app-icon {
  display: inline-block;
  vertical-align: -2px;
  flex: none;
}
</style>
