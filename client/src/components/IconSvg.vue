<template>
  <!-- 纯展示组件：不向外派发任何事件（MP-017，可点元素由父级自行绑定 @tap） -->
  <view class="icon-svg" :style="rootStyle">
    <!-- 微信小程序无原生 <svg> 组件，改用 <image> + SVG data-uri 渲染矢量图标，
         真机稳定且支持通过 color 注入描边色。 -->
    <image class="icon-svg-el" :src="dataUri" mode="aspectFit" :style="imgStyle" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * IconSvg —— 统一矢量图标组件（task-14 W1 / task-13 T27/T29 / ui-design-discussion §0.5）
 *
 * 设计约束：
 *  - 全部图标为线性 SVG（24px 网格、2px 描边、圆角端点一致），替代 Unicode emoji。
 *  - 通过 stroke 注入颜色，支持随主题 / 语义变色（如喜欢=红）。
 *  - 微信小程序不支持原生 <svg> 组件，故改用 <image> + SVG data-uri 渲染，
 *    真机零加载、可变色；内联 ICONS map 为唯一真源，assets/icons/*.svg 冗余副本已清理。
 *
 * 用法：<IconSvg name="thumb" :size="26" color="currentColor" />
 */

// 24px 网格下各图标 path（唯一真源，无外部 .svg 依赖）
// 2026-09-14（P3-05 / PR-05「零消费即删」）：'heart-filled' / 'heart' / 'send-simple' 三键已删除——
// 逐一核查确认端上零 `name="..."` 引用（收藏功能全量移除、评价发送键未启用）；
// 'thumb-filled' 亦零引用，但**「有用」按钮（唯一 UGC 互动，ReviewItem.vue）实际引用的是 'thumb' 线性键**，
// 故此处保留 'thumb'（点赞/有用语义唯一图标），仅删除 'thumb-filled' 填充变体。
// 同批删除的其余零消费键：'send'（评价发送，改用文本提交）、'up'（原回顶按钮已移除）、
// 'lightbulb'（线性灯泡，实色 lightbulb-fill 在用）、'contact'（联系开发者独立入口已下线）。
// ⚠️ 'home-filled' / 'profile-filled' 必须保留：TabBar.vue 以 `${icon}-filled` 动态拼接选中态图标，
//    静态 grep 会误判为零消费（P0-06 点赞图标同类陷阱）。
const ICONS: Record<string, { path?: string[]; fill?: boolean; circle?: { cx: number; cy: number; r: number; fill?: string }[] }> = {
  thumb: { path: ['M7 10v11', 'M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88z'] },
  search: { path: ['M11 11m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0', 'm21 21-4.35-4.35'] },
  arrow: { path: ['m9 18 6-6-6-6'] },
  close: { path: ['M18 6 6 18', 'm6 6 12 12'] },
  // 原 `filter`（漏斗）键已于 2026-09-14 删除：唯一消费点 FilterBar 的假控件胶囊已按 P0-05 移除，
  // 成为零消费键（PR-05：零消费图标不留存）。
  comment: { path: ['M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'] },
  report: { path: ['M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z', 'M12 9v4', 'M12 17h.01'] },
  plus: { path: ['M12 5v14', 'M5 12h14'] },
  location: { path: ['M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z', 'M12 10m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0'] },
  star: { path: ['M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z'] },
  // 实心星星（填充黄，E16）：与 star 同形，fill 实心渲染（展示用评分星）
  'star-filled': { path: ['M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z'], fill: true },
  home: { path: ['M3 9.5 12 3l9 6.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'] },
  profile: { path: ['M12 8m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0', 'M4 21a8 8 0 0 1 16 0'] },
  fire: { path: ['M12 2s4 4 4 8a4 4 0 0 1-8 0c0-1 .5-2 1-3-2 1-4 3-4 6a7 7 0 0 0 14 0c0-5-7-11-7-11z'] },
  clock: { path: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0', 'M12 7v5l3 2'] },
  price: { path: ['M12 1v22', 'M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6'] },
  edit: { path: ['M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7', 'M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4z'] },
  delete: { path: ['M3 6h18', 'M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2', 'M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6', 'M10 11v6', 'M14 11v6'] },
  check: { path: ['M20 6 9 17l-5-5'] },
  share: { path: ['M18 5m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'M6 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'M18 19m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'm8.6 13.5 6.8 4', 'M15.4 6.5l-6.8 4'] },
  dish: { path: ['M3 11h18a9 9 0 0 1-18 0z', 'M12 3v3', 'M5 21h14'] },
  image: { path: ['M3 3h18a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M9 9m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0', 'm21 15-5-5L5 21'] },
  // ── task-14 / ui-design-discussion §0.5 补充语义图标 ──
  // 返回（左箭头，区别于 back 的右箭头）
  'arrow-left': { path: ['m15 18-6-6 6-6'] },
  // 向下箭头（下拉关闭提示：不依赖 rotate，微信小程序 transform 方向不可靠）
  'arrow-down': { path: ['m6 9 6 6 6-6'] },
  // 向上箭头（下拉展开提示：与 arrow-down 垂直镜像，同样不依赖 rotate）
  'arrow-up': { path: ['m6 15 6-6 6 6'] },
  // 用户（人形，语义：账号/我的）
  user: { path: ['M12 8m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0', 'M4 21a8 8 0 0 1 16 0'] },
  // 锁（密码）
  lock: { path: ['M7 11V8a5 5 0 0 1 10 0v3', 'M5 11h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z', 'M12 16v1.5'] },
  // 铃铛（通知/提醒）
  bell: { path: ['M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9', 'M13.7 21a2 2 0 0 1-3.4 0'] },
  // 空状态（无数据 / 空盒子）：中性线性占位，区别于 dish 碗
  empty: { path: ['M3 10.5 12 4l9 6.5', 'M5 9.5V19a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5', 'M9 20v-6h6v6'] },
  // ── task-15 emoji→IconSvg 迁移补充图标 ──
  // 档口（店铺）
  stall: { path: ['M3 9l1.5-4.5A2 2 0 0 1 6.4 3h11.2a2 2 0 0 1 1.9 1.5L21 9', 'M4 9h16v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z', 'M9 13h6v4'] },
  // 食堂（楼栋/餐厅）：区别于 stall 店铺、home 房屋；带入口门与二楼窗
  canteen: { path: ['M4 21V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v15', 'M8 21v-5h8v5', 'M9 9h2', 'M13 9h2'] },
  // 更多（三点竖排，语义：评价/内容右上角更多操作）
  'more-v': { circle: [{ cx: 12, cy: 5, r: 1.4, fill: 'currentColor' }, { cx: 12, cy: 12, r: 1.4, fill: 'currentColor' }, { cx: 12, cy: 19, r: 1.4, fill: 'currentColor' }] },
  // ── tab-pages-visual-unify：底部导航选中态填充变体（与同名线性键配对，TabBar 按 active 切换） ──
  // 首页（房屋实心；下方门洞因路径内凹而自然留白）
  'home-filled': { path: ['M3 9.5 12 3l9 6.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'], fill: true },
  // 我的（人形实心：头部为实心圆 + 肩部闭合半圆，避免填充开放弧线导致形状畸变）
  'profile-filled': { path: ['M4 21a8 8 0 0 1 16 0z'], circle: [{ cx: 12, cy: 8, r: 4, fill: 'currentColor' }], fill: true },
  // ── feedback-forms-ux-polish：圆润填充（胖）glyph（意见反馈页顶部/选项等使用；SVG data-uri，禁 emoji） ──
  // 灯泡实心（提个想法）：圆润灯身 + 灯座
  'lightbulb-fill': { path: ['M12 3.4a6.6 6.6 0 0 0-4.7 11.3c1 1 1.6 2 1.8 3.1h5.8c.2-1.1.8-2.1 1.8-3.1A6.6 6.6 0 0 0 12 3.4z', 'M9.4 20h5.2c.1.9 0 1.5-.6 1.8-.7.4-3.3.4-4 0-.6-.3-.7-.9-.6-1.8z'], fill: true },
  // 碗盘实心（推荐菜品）：一碗米饭 + 顶部热气
  'dish-fill': { path: ['M4 10.8h16a9 9 0 0 1-18 0z', 'M12 2.6c-.8 0-1.4.6-1.4 1.4v2a1.4 1.4 0 0 0 2.8 0V4c0-.8-.6-1.4-1.4-1.4z'], fill: true },
  // 警示三角实心（信息不对）：实心三角形（白色感叹号由上层文本/间距表达）
  'report-fill': { path: ['M10.6 3.8a2 2 0 0 1 2.8 0l6.4 7a2 2 0 0 1 0 2.8l-6.4 7a2 2 0 0 1-2.8 0l-6.4-7a2 2 0 0 1 0-2.8z'], fill: true },
  // 搜索放大镜实心（信息不对搜索菜品）：实心镜片 + 手柄（手柄以端部圆点表达，避免开放式描边）
  'search-fill': { circle: [{ cx: 10.5, cy: 10.5, r: 6, fill: 'currentColor' }], path: ['M14.8 14.8l5.7 5.7'], fill: false },
}

// 描边色兜底常量（theme/tokens.ts 登记；MP-11 删除 resolveColor/COLOR_MAP 死机制后唯一色源）
import { ICON_FALLBACK_COLOR } from '@/theme/tokens'

const props = withDefaults(defineProps<{
  /** 图标名（见 ICONS 键） */
  name: string
  /** 尺寸（px 或 rpx 值，默认 32rpx） */
  size?: number | string
  /** 颜色（支持 CSS 变量名或真实色值），默认跟随文字色 */
  color?: string
}>(), {
  size: 32,
  color: 'currentColor',
})

const viewBox = 24
// 开发期告警：未知图标名会静默回退到 empty（空盒）图标，难以及时发现。
// 仅开发环境告警，生产环境保持静默回退，渲染不中断。
if (props.name && !ICONS[props.name]) {
  // uni-app 支持 import.meta.env.DEV；?. 容错避免非 Vite 环境报错
  if (import.meta.env?.DEV) {
    console.warn('[IconSvg] unknown icon name:', props.name)
  }
}
const icon = computed(() => ICONS[props.name] || ICONS.empty)
// MP-11：resolveColor/COLOR_MAP 死机制已删除——COLOR_MAP 键无 `--` 前缀，var() 查找从未命中，
// var() 形态实际恒走 currentColor 兜底。SVG data-uri 无法解析 var()，var() 形态统一落到
// 登记的兜底常量 ICON_FALLBACK_COLOR（与删除前的兜底同为中性近黑，渲染行为不变）；
// 其余形态（currentColor / 真实色值）原样透传。
const stroke = computed(() => {
  const c = props.color
  return !c || c.startsWith('var(') ? ICON_FALLBACK_COLOR : c
})

// 动态拼接 SVG 字符串并编码为 data-uri，供 <image> 渲染。
// MP-019：模块级缓存（icon name + 颜色 → data-uri）——百级卡片列表（瀑布流点赞星标等）
// 中同名同色图标不再逐实例重复「拼串 + encodeURIComponent」，命中直接复用。
// 键空间有界（ICONS 枚举固定 × token 色值固定），无需 LRU 淘汰。
const dataUriCache = new Map<string, string>()

/** 纯函数：由图标定义 + 描边色构建 data-uri（不读组件状态，供缓存复用） */
function buildDataUri(iconDef: typeof ICONS[string], strokeColor: string): string {
  const fillMode = iconDef.fill
  const paths = (iconDef.path || [])
    .map((d) => fillMode
      ? `<path d="${d}" fill="${strokeColor}" stroke="none"/>`
      : `<path d="${d}" fill="none" stroke="${strokeColor}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`)
    .join('')
  const circles = (iconDef.circle || [])
    .map((c) => `<circle cx="${c.cx}" cy="${c.cy}" r="${c.r}" fill="${c.fill === 'currentColor' ? strokeColor : (c.fill || 'none')}" ${c.fill ? '' : `stroke="${strokeColor}"`} stroke-width="2"/>`)
    .join('')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${viewBox} ${viewBox}" width="${viewBox}" height="${viewBox}">${paths}${circles}</svg>`
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
  color: props.color,
}))

const imgStyle = computed(() => ({
  width: '100%',
  height: '100%',
}))
</script>

<style scoped>
.icon-svg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  -webkit-tap-highlight-color: transparent;
}
.icon-svg-el {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
