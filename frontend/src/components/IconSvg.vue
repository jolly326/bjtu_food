<template>
  <view class="icon-svg" :style="rootStyle" @tap="onClick">
    <svg
      class="icon-svg-el"
      :viewBox="`0 0 ${viewBox} ${viewBox}`"
      fill="none"
      :stroke="color"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path v-for="(d, i) in pathList" :key="i" :d="d" />
      <circle
        v-for="(c, i) in circleList"
        :key="`c${i}`"
        :cx="c.cx"
        :cy="c.cy"
        :r="c.r"
        :fill="c.fill || 'none'"
        :stroke="c.fill ? 'none' : color"
      />
    </svg>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * IconSvg —— 统一矢量图标组件（task-14 W1 / task-13 T27/T29 / ui-design-discussion §0.5）
 *
 * 设计约束：
 *  - 全部图标为线性 SVG（24px 网格、2px 描边、圆角端点一致），替代 Unicode emoji。
 *  - 以 currentColor 语义通过 stroke 注入颜色，支持随主题 / 语义变色（如喜欢=红）。
 *  - 小程序 mp-weixin 不支持直接 <image> 改色且外部 svg 加载不稳定，故采用「内联 path」
 *    渲染，真机零加载、可变色；源文件同时落 frontend/src/assets/icons/* 备查登记。
 *
 * 用法：<IconSvg name="heart" :size="32" color="var(--color-like)" />
 */

// 24px 网格下各图标 path（与 frontend/src/assets/icons/*.svg 内容一致）
const ICONS: Record<string, { path?: string[]; circle?: { cx: number; cy: number; r: number; fill?: string }[] }> = {
  heart: { path: ['M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 1 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z'] },
  thumb: { path: ['M7 10v11', 'M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88z'] },
  search: { path: ['M11 11m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0', 'm21 21-4.35-4.35'] },
  arrow: { path: ['m9 18 6-6-6-6'] },
  close: { path: ['M18 6 6 18', 'm6 6 12 12'] },
  filter: { path: ['M22 3H2l8 9.46V19l4 2v-8.54L22 3z'] },
  comment: { path: ['M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'] },
  report: { path: ['M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z', 'M12 9v4', 'M12 17h.01'] },
  plus: { path: ['M12 5v14', 'M5 12h14'] },
  back: { path: ['m15 18-6-6 6-6'] },
  location: { path: ['M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z', 'M12 10m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0'] },
  star: { path: ['M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z'] },
  broadcast: { path: ['M4 11a9 9 0 0 1 9 9', 'M4 4a16 16 0 0 1 16 16'], circle: [{ cx: 5, cy: 19, r: 1.5, fill: 'currentColor' }] },
  home: { path: ['M3 9.5 12 3l9 6.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'] },
  profile: { path: ['M12 8m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0', 'M4 21a8 8 0 0 1 16 0'] },
  fire: { path: ['M12 2s4 4 4 8a4 4 0 0 1-8 0c0-1 .5-2 1-3-2 1-4 3-4 6a7 7 0 0 0 14 0c0-5-7-11-7-11z'] },
  clock: { path: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0', 'M12 7v5l3 2'] },
  price: { path: ['M12 1v22', 'M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6'] },
  edit: { path: ['M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7', 'M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4z'] },
  delete: { path: ['M3 6h18', 'M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2', 'M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6', 'M10 11v6', 'M14 11v6'] },
  check: { path: ['M20 6 9 17l-5-5'] },
  share: { path: ['M18 5m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'M6 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'M18 19m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'm8.6 13.5 6.8 4', 'M15.4 6.5l-6.8 4'] },
  lightbulb: { path: ['M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5', 'M9 18h6', 'M10 22h4'] },
  dish: { path: ['M3 11h18a9 9 0 0 1-18 0z', 'M12 3v3', 'M5 21h14'] },
  empty: { path: ['M3 11h18a9 9 0 0 1-18 0z', 'M12 3v3', 'M5 21h14'] },
  image: { path: ['M3 3h18a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M9 9m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0', 'm21 15-5-5L5 21'] },
}

const props = withDefaults(defineProps<{
  /** 图标名（见 ICONS 键） */
  name: string
  /** 尺寸（px 或 rpx 值，默认 32rpx） */
  size?: number | string
  /** 颜色（支持 CSS 变量），默认跟随文字色 currentColor */
  color?: string
}>(), {
  size: 32,
  color: 'currentColor',
})

const emit = defineEmits<{ (e: 'click'): void }>()

const viewBox = 24
const icon = computed(() => ICONS[props.name] || ICONS.empty)
const pathList = computed(() => icon.value.path || [])
const circleList = computed(() => icon.value.circle || [])

const rootStyle = computed(() => ({
  width: typeof props.size === 'number' ? `${props.size}rpx` : props.size,
  height: typeof props.size === 'number' ? `${props.size}rpx` : props.size,
  display: 'inline-flex',
  'align-items': 'center',
  'justify-content': 'center',
  color: props.color,
}))

function onClick() {
  emit('click')
}
</script>

<style scoped>
.icon-svg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
.icon-svg-el {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
