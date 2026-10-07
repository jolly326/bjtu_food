<template>
  <!-- 纯展示组件：不向外派发任何事件（可点元素由父级自行绑定 @tap） -->
  <view class="app-icon" :style="rootStyle">
    <!-- 微信小程序无原生 <svg> 组件，改用 <image> + SVG data-uri 渲染矢量图标，
         真机稳定且支持通过 color 注入描边色。 -->
    <image class="app-icon-el" :src="dataUri" mode="aspectFit" :style="imgStyle" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * AppIcon —— 全站唯一矢量图标组件（IconPark 官方线性主题，48 网格 / 2 描边 / 圆角端点）。
 *
 * 图标来源与许可证：IconPark（字节跳动开源图标库，Apache License 2.0，可商用），
 * 主题 = outline；几何为官方产物中的路径数据，随本组件内联（无外部 .svg 依赖、无字体文件）。
 * 来源登记见 docs/ui/client/公共组件与形态基线.md §1.14。
 *
 * 用法：<AppIcon name="search" :size="40" :color="COLOR_MAP['text-tertiary']" />
 *
 * **尺寸与颜色的契约**：
 *  - `size`：数字 = **rpx**（`:size="40"` → 40rpx），也可传带单位字符串（`size="20px"`）。
 *  - `color`：**必须传实色**（`COLOR_MAP['xxx']` 或 `#RRGGBB`）。`var(--x)` 与 `currentColor`
 *    都会被回退为兜底近黑色 —— SVG data-uri 是独立文档，解析不了 `var()`、也继承不到父级文字色。
 *  - 居中 / 防压缩由根节点 inline-flex + `flex: none` 内联自持（不依赖消费方样式）。
 *    ⚠️ 组件**未开启** `virtualHost`（当前 uni-app 版本两种写法都不会写入产物 json）
 *    ⇒ flex 父级里会多一层宿主节点；图标不居中时用消费方 class 把宿主定为 flex 盒。
 */

/** 单个图标的官方几何（路径 + 圆 + 矩形；`fill: true` = 同套几何的主轮廓填色变体） */
interface IconGeometry {
  path?: string[]
  circle?: Array<{ cx: number; cy: number; r: number }>
  rect?: Array<{ x: number; y: number; width: number; height: number; rx: number }>
  fill?: boolean
}

/**
 * 图标键 → 官方几何（唯一真源，内联自持）。
 *
 * 登记口径：零消费即不登记；每个键对应一个消费语义。
 * ⚠️ 一类键**必须保留**，静态 grep 会误判为零消费：
 *  - `home-filled` / `profile-filled`：TabBar 以 `${icon}-filled` 动态拼接选中态。
 *  - `empty`：未知图标名的兜底回退目标（由下方 `icon` computed 内部引用，非业务消费）。
 *
 * **尺寸档位表见 docs/ui/client/公共组件与形态基线.md §1.14** —— `size` 只允许取表内档位。
 */
const ICONS: Record<string, IconGeometry> = {
  search: { path: ['M21 38C30.3888 38 38 30.3888 38 21C38 11.6112 30.3888 4 21 4C11.6112 4 4 11.6112 4 21C4 30.3888 11.6112 38 21 38Z', 'M26.657 14.3431C25.2093 12.8954 23.2093 12 21.0001 12C18.791 12 16.791 12.8954 15.3433 14.3431', 'M33.2216 33.2217L41.7069 41.707'] },
  close: { path: ['M8 8L40 40', 'M8 40L40 8'] },
  check: { path: ['M43 11L16.875 37L5 25.1818'] },
  plus: { path: ['M24.0605 10L24.0239 38', 'M10 24L38 24'] },
  edit: { path: ['M7 42H43', 'M11 26.7199V34H18.3172L39 13.3081L31.6951 6L11 26.7199Z'] },
  delete: { path: ['M9 10V44H39V10H9Z', 'M20 20V33', 'M28 20V33', 'M4 10H44', 'M16 10L19.289 4H28.7771L32 10H16Z'] },
  arrow: { path: ['M19 12L31 24L19 36'] },
  'arrow-up': { path: ['M13 30L25 18L37 30'] },
  'arrow-down': { path: ['M36 18L24 30L12 18'] },
  star: { path: ['M23.9986 5L17.8856 17.4776L4 19.4911L14.0589 29.3251L11.6544 43L23.9986 36.4192L36.3454 43L33.9586 29.3251L44 19.4911L30.1913 17.4776L23.9986 5Z'] },
  'star-filled': { path: ['M23.9986 5L17.8856 17.4776L4 19.4911L14.0589 29.3251L11.6544 43L23.9986 36.4192L36.3454 43L33.9586 29.3251L44 19.4911L30.1913 17.4776L23.9986 5Z'], fill: true },
  home: { path: ['M9 18V42H39V18L24 6L9 18Z', 'M19 29V42H29V29H19Z', 'M9 42H39'] },
  'home-filled': { path: ['M9 18V42H39V18L24 6L9 18Z', 'M19 29V42H29V29H19Z', 'M9 42H39'], fill: true },
  profile: { path: ['M42 44C42 34.0589 33.9411 26 24 26C14.0589 26 6 34.0589 6 44'], circle: [{ cx: 24, cy: 12, r: 8 }] },
  'profile-filled': { path: ['M42 44C42 34.0589 33.9411 26 24 26C14.0589 26 6 34.0589 6 44'], circle: [{ cx: 24, cy: 12, r: 8 }], fill: true },
  user: { path: ['M42 44C42 34.0589 33.9411 26 24 26C14.0589 26 6 34.0589 6 44'], circle: [{ cx: 24, cy: 12, r: 8 }] },
  lock: { path: ['M14 22V14C14 8.47715 18.4772 4 24 4C29.5228 4 34 8.47715 34 14V22', 'M24 30V36'], rect: [{ x: 6, y: 22, width: 36, height: 22, rx: 2 }] },
  bell: { path: ['M14 25C14 19.4772 18.4772 15 24 15C29.5228 15 34 19.4772 34 25V41H14V25Z', 'M24 5V8', 'M35.8918 9.32823L33.9634 11.6264', 'M42.2187 20.2873L39.2642 20.8083', 'M5.78116 20.2874L8.73558 20.8083', 'M12.1086 9.32802L14.037 11.6262', 'M6 41H43'] },
  comment: { path: ['M44 6H4V36H13V41L23 36H44V6Z', 'M14 19.5V22.5', 'M24 19.5V22.5', 'M34 19.5V22.5'] },
  report: { path: ['M24 5L2 43H46L24 5Z', 'M24 35V36', 'M24 19.0005L24.0083 29'] },
  alert: { path: ['M24 44C29.5228 44 34.5228 41.7614 38.1421 38.1421C41.7614 34.5228 44 29.5228 44 24C44 18.4772 41.7614 13.4772 38.1421 9.85786C34.5228 6.23858 29.5228 4 24 4C18.4772 4 13.4772 6.23858 9.85786 9.85786C6.23858 13.4772 4 18.4772 4 24C4 29.5228 6.23858 34.5228 9.85786 38.1421C13.4772 41.7614 18.4772 44 24 44Z', 'M24 37C25.3807 37 26.5 35.8807 26.5 34.5C26.5 33.1193 25.3807 32 24 32C22.6193 32 21.5 33.1193 21.5 34.5C21.5 35.8807 22.6193 37 24 37Z', 'M24 12V28'] },
  location: { path: ['M9.85786 32.7574C6.23858 33.8432 4 35.3432 4 37C4 40.3137 12.9543 43 24 43C35.0457 43 44 40.3137 44 37C44 35.3432 41.7614 33.8432 38.1421 32.7574', 'M24 35C24 35 37 26.504 37 16.6818C37 9.67784 31.1797 4 24 4C16.8203 4 11 9.67784 11 16.6818C11 26.504 24 35 24 35Z', 'M24 22C26.7614 22 29 19.7614 29 17C29 14.2386 26.7614 12 24 12C21.2386 12 19 14.2386 19 17C19 19.7614 21.2386 22 24 22Z'] },
  dish: { path: ['M32 44C32 44 32 40.1355 32.0015 40.1282C32.6987 39.7817 33.3702 39.3913 34.0124 38.9606C34.9896 38.3053 35.899 37.5569 36.7279 36.7279C39.9853 33.4706 42 28.9706 42 24H6C6 28.9706 8.01472 33.4706 11.2721 36.7279C12.1086 37.5645 13.0271 38.319 14.0145 38.9786C14.653 39.4051 15.3204 39.792 16.0131 40.1355L16 44H32Z', 'M24 18.0083V8', 'M36 18.0083V12', 'M12 18.0083V12', 'M40 8C37.7909 8 36 9.79086 36 12', 'M28 4C25.7909 4 24 5.79086 24 8', 'M16 8C13.7909 8 12 9.79086 12 12'] },
  image: { path: ['M5 10C5 8.89543 5.89543 8 7 8L41 8C42.1046 8 43 8.89543 43 10V38C43 39.1046 42.1046 40 41 40H7C5.89543 40 5 39.1046 5 38V10Z', 'M14.5 18C15.3284 18 16 17.3284 16 16.5C16 15.6716 15.3284 15 14.5 15C13.6716 15 13 15.6716 13 16.5C13 17.3284 13.6716 18 14.5 18Z', 'M15 24L20 28L26 21L43 34V38C43 39.1046 42.1046 40 41 40H7C5.89543 40 5 39.1046 5 38V34L15 24Z'] },
  camera: { path: ['M15 12L18 6H30L33 12H15Z', 'M24 35C28.4183 35 32 31.4183 32 27C32 22.5817 28.4183 19 24 19C19.5817 19 16 22.5817 16 27C16 31.4183 19.5817 35 24 35Z'], rect: [{ x: 4, y: 12, width: 40, height: 30, rx: 3 }] },
  'image-broken': { path: ['M44 23.9941C44 22.8896 43.1046 21.9941 42 21.9941C40.8954 21.9941 40 22.8896 40 23.9941H44ZM24 7.99414C25.1046 7.99414 26 7.09871 26 5.99414C26 4.88957 25.1046 3.99414 24 3.99414V7.99414ZM39 39.9941H9V43.9941H39V39.9941ZM8 38.9941V8.99414H4V38.9941H8ZM40 23.9941V38.9941H44V23.9941H40ZM9 7.99414H24V3.99414H9V7.99414ZM9 39.9941C8.44772 39.9941 8 39.5464 8 38.9941H4C4 41.7556 6.23857 43.9941 9 43.9941V39.9941ZM39 43.9941C41.7614 43.9941 44 41.7556 44 38.9941H40C40 39.5464 39.5523 39.9941 39 39.9941V43.9941ZM8 8.99414C8 8.44186 8.44771 7.99414 9 7.99414V3.99414C6.23858 3.99414 4 6.23272 4 8.99414H8Z', 'M6 35L16.6931 25.198C17.4389 24.5143 18.5779 24.4953 19.3461 25.1538L32 36', 'M28 31L32.7735 26.2265C33.4772 25.5228 34.5914 25.4436 35.3877 26.0408L42 31', 'M33 7L41 15', 'M41 7L33 15'] },
  'more-v': { path: ['M24 44C35.0457 44 44 35.0457 44 24C44 12.9543 35.0457 4 24 4C12.9543 4 4 12.9543 4 24C4 35.0457 12.9543 44 24 44Z', 'M21.5 14C21.5 15.3807 22.6193 16.5 24 16.5C25.3807 16.5 26.5 15.3807 26.5 14C26.5 12.6193 25.3807 11.5 24 11.5C22.6193 11.5 21.5 12.6193 21.5 14ZM21.5 24C21.5 25.3807 22.6193 26.5 24 26.5C25.3807 26.5 26.5 25.3807 26.5 24C26.5 22.6193 25.3807 21.5 24 21.5C22.6193 21.5 21.5 22.6193 21.5 24ZM24 36.5C22.6193 36.5 21.5 35.3807 21.5 34C21.5 32.6193 22.6193 31.5 24 31.5C25.3807 31.5 26.5 32.6193 26.5 34C26.5 35.3807 25.3807 36.5 24 36.5Z'] },
  certificate: { path: ['M26 36H6C4.89543 36 4 35.1046 4 34V8C4 6.89543 4.89543 6 6 6H42C43.1046 6 44 6.89543 44 8V34C44 35.1046 43.1046 36 42 36H34', 'M12 14H36', 'M12 21H18', 'M12 28H16', 'M30 33C33.3137 33 36 30.3137 36 27C36 23.6863 33.3137 21 30 21C26.6863 21 24 23.6863 24 27C24 30.3137 26.6863 33 30 33Z', 'M30 40L34 42V31.4722C34 31.4722 32.8594 33 30 33C27.1406 33 26 31.5 26 31.5V42L30 40Z'] },
  lightbulb: { path: ['M40 20C40 26.8077 35.7484 32.6224 29.7555 34.9336H24H18.2445C12.2516 32.6224 8 26.8077 8 20C8 11.1634 15.1634 4 24 4C32.8366 4 40 11.1634 40 20Z', 'M29.7557 34.9336L29.0766 43.0831C29.0334 43.6014 28.6001 44 28.08 44H19.9203C19.4002 44 18.9669 43.6014 18.9238 43.0831L18.2446 34.9336', 'M18 17V23L24 20L30 23V17'] },
  empty: { path: ['M8 6C8 4.89543 8.89543 4 10 4H30L40 14V42C40 43.1046 39.1046 44 38 44H10C8.89543 44 8 43.1046 8 42V6Z', 'M16 20H32', 'M16 28H32'] },
}

// 描边色兜底常量（theme/tokens.ts 登记；当前唯一色源）
import { ICON_FALLBACK_COLOR } from '@/theme/tokens'

const props = withDefaults(defineProps<{
  /** 图标名（见 ICONS 键） */
  name: string
  /** 尺寸档位（数字 = rpx，默认 32rpx；允许档位见 docs/ui/client/公共组件与形态基线.md §1.14） */
  size?: number | string
  /** 描边色**必须传实色**（`COLOR_MAP['xxx']` 或 `#RRGGBB`）；缺省 / `currentColor` / `var(...)` 一律回退兜底近黑 */
  color?: string
}>(), {
  size: 32,
  color: 'currentColor',
})

const viewBox = 48
// 开发期告警：未知图标名会静默回退到 empty（空数据）图标，难以及时发现。
// 仅开发环境告警，生产环境保持静默回退，渲染不中断。
if (props.name && !ICONS[props.name]) {
  // uni-app 支持 import.meta.env.DEV；?. 容错避免非 Vite 环境报错
  if (import.meta.env?.DEV) {
    console.warn('[AppIcon] unknown icon name:', props.name)
  }
}
const icon = computed(() => ICONS[props.name] || ICONS.empty)
/**
 * 颜色解析：
 * SVG 走 data-uri ⇒ 它是一份**独立文档**，既**解析不了 `var()`**，也**继承不到父级文字色**
 * —— 故 `var(...)` 与 `currentColor`（默认值）都统一落到兜底常量 `ICON_FALLBACK_COLOR`（中性近黑）。
 * 需要语义色时**必须传实色**（如 `COLOR_MAP['text-placeholder']`，见 theme/tokens.ts）。
 */
const stroke = computed(() => {
  const c = props.color
  return !c || c === 'currentColor' || c.startsWith('var(') ? ICON_FALLBACK_COLOR : c
})

// 开发期告警：传 `var(...)` 是最常见的误用（静默变近黑、难排查），仅 DEV 提示，生产保持静默
if (import.meta.env?.DEV && props.color?.startsWith('var(')) {
  console.warn('[AppIcon] color 不支持 var()（SVG data-uri 是独立文档），已回退兜底色；请传实色（COLOR_MAP）:', props.name)
}

// 动态拼接 SVG 字符串并编码为 data-uri，供 <image> 渲染。
// 百级卡片列表（瀑布流评分星标等）中同名同色图标不逐实例重复「拼串 + encodeURIComponent」，命中直接复用。
// 键空间有界（ICONS 键固定 × token 色值固定），无需 LRU 淘汰。
const dataUriCache = new Map<string, string>()

/** 纯函数：由图标几何 + 描边色构建 data-uri（不读组件状态，供缓存复用） */
function buildDataUri(geometry: IconGeometry, color: string): string {
  const fillMode = geometry.fill === true
  // 线性变体统一 round cap / round join（IconPark 官方观感）；实心变体不描边、无端点概念
  const paint = fillMode
    ? `fill="${color}" stroke="none"`
    : `fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"`
  const paths = (geometry.path || []).map((d) => `<path d="${d}" ${paint}/>`).join('')
  const circles = (geometry.circle || []).map((c) => `<circle cx="${c.cx}" cy="${c.cy}" r="${c.r}" ${paint}/>`).join('')
  const rects = (geometry.rect || [])
    .map((r) => `<rect x="${r.x}" y="${r.y}" width="${r.width}" height="${r.height}" rx="${r.rx}" ${paint}/>`)
    .join('')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${viewBox} ${viewBox}" width="${viewBox}" height="${viewBox}">${paths}${circles}${rects}</svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

const dataUri = computed(() => {
  // key 用原始 name（未知名回退 empty 后仍按 name 区分键，同 name 必同 icon，缓存正确）
  const key = `${props.name}|${stroke.value}`
  let uri = dataUriCache.get(key)
  if (uri === undefined) {
    uri = buildDataUri(icon.value, stroke.value)
    dataUriCache.set(key, uri)
  }
  return uri
})

const rootStyle = computed(() => ({
  width: typeof props.size === 'number' ? `${props.size}rpx` : props.size,
  height: typeof props.size === 'number' ? `${props.size}rpx` : props.size,
  display: 'inline-flex',
  'align-items': 'center',
  'justify-content': 'center',
  // 内联自持 `flex: none`：任何 flex 父级下都不被压缩 —— 消费方**无需**再写任何包装样式
  flex: 'none',
  color: props.color,
}))

const imgStyle = computed(() => ({
  width: '100%',
  height: '100%',
}))
</script>

<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  -webkit-tap-highlight-color: transparent;
}
.app-icon-el {
  width: 100%;
  height: 100%;
  display: block;
}
</style>