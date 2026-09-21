<template>
  <!-- 首页 Banner 右侧装饰插画（**占位实现**，2026-09-21 §7.34 / G6）。
       机制与 IconSvg 一致：微信小程序无原生 <svg> 组件 → 走 <image> + SVG data-uri（真机零加载）。
       风格：线性、圆角端点/连接，与全站图标同一视觉语言；意象 = 面条 + 饮品（呼应「今日推荐」）。
       替换为正式资产时只改本组件内部，不触碰首页结构（PR-05：占位实现须登记，不留零消费资产）。 -->
  <view class="banner-art" :style="rootStyle" aria-hidden="true">
    <image class="banner-art-el" :src="dataUri" mode="aspectFit" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { COLOR_MAP } from '@/theme/tokens'

const props = withDefaults(defineProps<{
  /** 显示尺寸（rpx；数字按 rpx 处理） */
  size?: number | string
  /**
   * 描边色。默认取 token 真源的主色（图形档）。
   * 注：SVG data-uri 内无法解析 var()，故此处传**真实色值**（来自 `COLOR_MAP` 而非裸 hex）。
   */
  color?: string
}>(), {
  size: 200,
  color: COLOR_MAP.primary,
})

const VIEW_BOX_W = 132
const VIEW_BOX_H = 112

/** 线性图形（碗 + 面 + 杯 + 吸管）；纯装饰，无交互 */
const PATHS = [
  // 桌面
  'M8 66 h84',
  // 碗体（下弧）
  'M14 66 Q50 100 86 66',
  // 面条（自碗中升起）
  'M36 58 q-3 -12 2 -22',
  'M50 58 q-3 -13 2 -24',
  'M64 58 q-3 -12 2 -22',
  // 杯身
  'M96 38 h26 l-4 34 c-0.5 4.5 -4.2 7.6 -8.6 7.6 h-2.8 c-4.4 0 -8.1 -3.1 -8.6 -7.6 Z',
  // 吸管
  'M109 38 V26',
  'M109 26 l8 -5',
]

const dataUri = computed(() => {
  const body = PATHS
    .map(d => `<path d="${d}" fill="none" stroke="${props.color}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>`)
    .join('')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${VIEW_BOX_W} ${VIEW_BOX_H}" width="${VIEW_BOX_W}" height="${VIEW_BOX_H}">${body}</svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
})

const rootStyle = computed(() => {
  const s = typeof props.size === 'number' ? `${props.size}rpx` : props.size
  return { width: s, height: s }
})
</script>

<style scoped>
.banner-art {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.banner-art-el {
  width: 100%;
  height: 100%;
}
</style>
