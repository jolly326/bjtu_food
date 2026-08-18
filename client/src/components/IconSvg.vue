<template>
  <view class="icon-svg" :style="rootStyle" @tap="onClick">
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
 *    真机零加载、可变色；源文件同时落 frontend/src/assets/icons/* 备查登记。
 *
 * 用法：<IconSvg name="heart" :size="32" color="var(--color-like)" />
 */

// 24px 网格下各图标 path（与 frontend/src/assets/icons/*.svg 内容一致）
const ICONS: Record<string, { path?: string[]; fill?: boolean; circle?: { cx: number; cy: number; r: number; fill?: string }[] }> = {
  heart: { path: ['M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 1 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z'] },
  // 实心喜欢（填充红，E16）：与 heart 同形，fill 实心渲染
  'heart-filled': { path: ['M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 1 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z'], fill: true },
  thumb: { path: ['M7 10v11', 'M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88z'] },
  search: { path: ['M11 11m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0', 'm21 21-4.35-4.35'] },
  arrow: { path: ['m9 18 6-6-6-6'] },
  // 向上箭头（回到顶部按钮）
  up: { path: ['m18 15-6-6-6 6'] },
  close: { path: ['M18 6 6 18', 'm6 6 12 12'] },
  filter: { path: ['M22 3H2l8 9.46V19l4 2v-8.54L22 3z'] },
  comment: { path: ['M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'] },
  report: { path: ['M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z', 'M12 9v4', 'M12 17h.01'] },
  plus: { path: ['M12 5v14', 'M5 12h14'] },
  back: { path: ['m15 18-6-6 6-6'] },
  location: { path: ['M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z', 'M12 10m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0'] },
  star: { path: ['M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z'] },
  // 实心星星（填充黄，E16）：与 star 同形，fill 实心渲染（展示用评分星）
  'star-filled': { path: ['M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z'], fill: true },
  broadcast: { path: ['M4 11a9 9 0 0 1 9 9', 'M4 4a16 16 0 0 1 16 16'], circle: [{ cx: 5, cy: 19, r: 1.5, fill: 'currentColor' }] },
  home: { path: ['M3 9.5 12 3l9 6.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'] },
  profile: { path: ['M12 8m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0', 'M4 21a8 8 0 0 1 16 0'] },
  fire: { path: ['M12 2s4 4 4 8a4 4 0 0 1-8 0c0-1 .5-2 1-3-2 1-4 3-4 6a7 7 0 0 0 14 0c0-5-7-11-7-11z'] },
  clock: { path: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0', 'M12 7v5l3 2'] },
  price: { path: ['M12 1v22', 'M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6'] },
  edit: { path: ['M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7', 'M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4z'] },
  delete: { path: ['M3 6h18', 'M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2', 'M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6', 'M10 11v6', 'M14 11v6'] },
  check: { path: ['M20 6 9 17l-5-5'] },
  // 复制（两重叠方块，语义：复制反馈内容）
  copy: { path: ['M9 9h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V11a2 2 0 0 1 2-2z', 'M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'] },
  share: { path: ['M18 5m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'M6 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'M18 19m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'm8.6 13.5 6.8 4', 'M15.4 6.5l-6.8 4'] },
  lightbulb: { path: ['M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5', 'M9 18h6', 'M10 22h4'] },
  dish: { path: ['M3 11h18a9 9 0 0 1-18 0z', 'M12 3v3', 'M5 21h14'] },
  image: { path: ['M3 3h18a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M9 9m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0', 'm21 15-5-5L5 21'] },
  // ── task-14 / ui-design-discussion §0.5 补充语义图标 ──
  // 返回（左箭头，区别于 back 的右箭头）
  'arrow-left': { path: ['m15 18-6-6 6-6'] },
  // 向下箭头（下拉关闭提示：不依赖 rotate，微信小程序 transform 方向不可靠）
  'arrow-down': { path: ['m6 9 6 6 6-6'] },
  // 用户（人形，语义：账号/我的）
  user: { path: ['M12 8m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0', 'M4 21a8 8 0 0 1 16 0'] },
  // 品牌标识（碗 + 热气，食在交大 logo）
  logo: { path: ['M3 11h18a9 9 0 0 1-18 0z', 'M5 21h14', 'M9 4c0 1-1 1.5-1 2.5', 'M12 3c0 1-1 1.5-1 2.5', 'M15 4c0 1-1 1.5-1 2.5'] },
  // 邮箱
  mail: { path: ['M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z', 'm3 7 9 6 9-6'] },
  // 锁（密码）
  lock: { path: ['M7 11V8a5 5 0 0 1 10 0v3', 'M5 11h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z', 'M12 16v1.5'] },
  // 铃铛（通知/提醒）
  bell: { path: ['M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9', 'M13.7 21a2 2 0 0 1-3.4 0'] },
  // 空状态（无数据 / 空盒子）：中性线性占位，区别于 dish 碗
  empty: { path: ['M3 10.5 12 4l9 6.5', 'M5 9.5V19a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5', 'M9 20v-6h6v6'] },
  // ── 八大菜品分类线性图标（禁止回退到 dish，必须各自独立语义） ──
  // 面条
  noodle: { path: ['M4 11h16a8 8 0 0 1-16 0z', 'M6 7c0 1 1 1 1 2', 'M10 7c0 1 1 1 1 2', 'M14 7c0 1 1 1 1 2', 'M18 7c0 1 1 1 1 2', 'M5 21h14'] },
  // 米饭
  rice: { path: ['M6 11h12a6 6 0 0 1-12 0z', 'M12 5v3', 'M9 7v1', 'M15 7v1', 'M6 20h12'] },
  // 麻辣烫（碗 + 串签）
  malatang: { path: ['M4 11h16a8 8 0 0 1-16 0z', 'M8 4v8', 'M12 4v9', 'M16 4v8', 'M5 21h14'] },
  // 早餐（鸡蛋煎盘）
  breakfast: { path: ['M4 12h16a8 8 0 0 1-16 0z', 'M9 8a3 3 0 0 1 6 0', 'M6 20h12', 'M12 3v2'] },
  // 夜宵（月亮 + 碗）
  midnight: { path: ['M20 14a8 8 0 1 1-9-11 6 6 0 0 0 9 11z', 'M4 18h7a3.5 3.5 0 0 1 0 7H4z'] },
  // 快餐（汉堡）
  fastfood: { path: ['M5 8h14', 'M4 8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2', 'M5 12h14', 'M4 16h16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z', 'M5 12v4'] },
  // 小吃（串签 + 方块）
  snack: { path: ['M7 3v18', 'M7 9 17 9', 'M11 5a2 2 0 0 0 4 0', 'M11 13a2 2 0 0 0 4 0', 'M11 17a2 2 0 0 0 4 0'] },
  // 饮品（杯子 + 吸管）
  drink: { path: ['M6 5h12l-1 15a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2z', 'M9 5a3 3 0 0 1 6 0', 'M16 8l4-2'] },
  // ── task-15 emoji→IconSvg 迁移补充图标 ──
  // 联系开发者（信封 + 对话）
  contact: { path: ['M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z', 'm3 7 9 6 9-6'] },
  // 设置（齿轮）
  settings: { path: ['M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z', 'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-2.82 1.17V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 8.4 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 3 15a1.65 1.65 0 0 0-1.51-1H1a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 3 8.4a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 8.4 3c.24 0 .47.04.69.1A1.65 1.65 0 0 0 10 1a2 2 0 0 1 4 0v.09A1.65 1.65 0 0 0 15.6 3a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 21 8.4V9a1.65 1.65 0 0 0 1.51 1H23a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z'] },
  // 清单 / 收藏夹
  list: { path: ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3 6h.01', 'M3 12h.01', 'M3 18h.01'] },
  // 辣度（辣椒）
  chili: { path: ['M8 12c0 4 3 7 7 7a4 4 0 0 0 4-4c0-3-2-5-4-6-2-1-3-2-3-4 0-1-1-2-2-2-3 0-5 3-5 9z', 'M11 9c-1 1-1 3 0 4'] },
  // 分量（餐盒）
  portion: { path: ['M3 8h18a1 1 0 0 1 1 1v8a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3v-8a1 1 0 0 1 1-1z', 'M3 8a9 9 0 0 1 18 0', 'M11 12v4'] },
  // 档口（店铺）
  stall: { path: ['M3 9l1.5-4.5A2 2 0 0 1 6.4 3h11.2a2 2 0 0 1 1.9 1.5L21 9', 'M4 9h16v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z', 'M9 13h6v4'] },
  // 食堂（楼栋/餐厅）：区别于 stall 店铺、home 房屋；带入口门与二楼窗
  canteen: { path: ['M4 21V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v15', 'M8 21v-5h8v5', 'M9 9h2', 'M13 9h2'] },
  // 更多（三点横排，语义：卡片右上角更多操作 / 溢出菜单）
  more: { circle: [{ cx: 5, cy: 12, r: 1.4, fill: 'currentColor' }, { cx: 12, cy: 12, r: 1.4, fill: 'currentColor' }, { cx: 19, cy: 12, r: 1.4, fill: 'currentColor' }] },
}

// CSS 变量 → 真实色值映射（覆盖项目主题主色，避免 SVG data-uri 无法解析 var()）
// 单一事实源：色值统一维护在 src/theme/tokens.ts（改主色只改一处，图标全同步）
// 深浅双模式：图标色随主题切换，深色模式不再沿用浅色真值导致脱色
import { ICON_COLOR_VARS as COLOR_VARS_TABLE } from '@/theme/tokens'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const COLOR_VARS = computed(() => COLOR_VARS_TABLE[themeStore.isDark ? 'dark' : 'light'])

function resolveColor(c: string): string {
  if (!c) return COLOR_VARS.value.currentColor || '#1C1C1E'
  if (c.startsWith('var(')) {
    const name = c.slice(4, -1).trim()
    return COLOR_VARS.value[name] || COLOR_VARS.value.currentColor || '#1C1C1E'
  }
  return c
}

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

const emit = defineEmits<{ (e: 'click'): void }>()

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
const stroke = computed(() => resolveColor(props.color))

// 动态拼接 SVG 字符串并编码为 data-uri，供 <image> 渲染
const dataUri = computed(() => {
  const fillMode = icon.value.fill
  const paths = (icon.value.path || [])
    .map((d) => fillMode
      ? `<path d="${d}" fill="${stroke.value}" stroke="none"/>`
      : `<path d="${d}" fill="none" stroke="${stroke.value}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`)
    .join('')
  const circles = (icon.value.circle || [])
    .map((c) => `<circle cx="${c.cx}" cy="${c.cy}" r="${c.r}" fill="${c.fill === 'currentColor' ? stroke.value : (c.fill || 'none')}" ${c.fill ? '' : `stroke="${stroke.value}"`} stroke-width="2"/>`)
    .join('')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${viewBox} ${viewBox}" width="${viewBox}" height="${viewBox}">${paths}${circles}</svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
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
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
/* 按压反馈：仅作视觉 scale，对装饰性图标无害；
   作为按钮使用的实例（返回/编辑箭头等）由父级 @tap 触发交互。 */
.icon-svg:active { transform: scale(var(--press-scale)); }
.icon-svg-el {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
