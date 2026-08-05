/**
 * 品牌主题色单一事实源（token 常量）
 * ------------------------------------------------------------------
 * 背景：小程序主色用于两处——App.vue 的 CSS 变量声明，以及 IconSvg.vue 的
 * SVG data-uri 图标（SVG 无法解析 var()，需真实色值）。此前两处各自硬编码，
 * 主色变更时若漏改其一，图标会与主题脱色。
 * 本文件作为唯一来源：改色只改这里，App.vue 与 IconSvg 均从此处读取。
 * 命名：同一色相轴（terracotta 陶土红，hue≈10°），浅/深模式仅明度/饱和有序推移。
 * ------------------------------------------------------------------
 */

/** 浅色模式主色（更鲜活，白字按钮 AA 达标，对比白底 5.08:1） */
export const COLOR_PRIMARY_LIGHT = '#C1442E'
/** 浅色主色暗阶（hover / 深按） */
export const COLOR_PRIMARY_DARK_LIGHT = '#9C2F1F'
/** 深色模式主色（同 hue 高明度，深底醒目；按钮须配深字，禁用白字） */
export const COLOR_PRIMARY_DARK = '#E08A6A'
/** 深色主色暗阶 */
export const COLOR_PRIMARY_DARK_DARK = '#B0553A'

/** 品牌辅助色：强调/热卖橙（浅色模式） */
export const COLOR_ACCENT_LIGHT = '#E67E22'
/** 品牌辅助色：强调/热卖橙（深色模式，稍提亮保证深底对比） */
export const COLOR_ACCENT_DARK = '#E8965C'

/**
 * 浅色模式 token 色值表（供 App.vue page/:root 声明）
 */
export const LIGHT_TOKENS: Record<string, string> = {
  '--color-primary': COLOR_PRIMARY_LIGHT,
  '--color-primary-dark': COLOR_PRIMARY_DARK_LIGHT,
  '--color-accent': COLOR_ACCENT_LIGHT,
  '--color-price': '#C0392B',
}

/**
 * 深色模式 token 色值表（供 App.vue theme-dark 声明）
 */
export const DARK_TOKENS: Record<string, string> = {
  '--color-primary': COLOR_PRIMARY_DARK,
  '--color-primary-dark': COLOR_PRIMARY_DARK_DARK,
  '--color-accent': COLOR_ACCENT_DARK,
  '--color-price': '#E5655A',
}

/**
 * IconSvg 图标色值表：SVG data-uri 无法解析 var()，须用真实色。
 * 深色模式下主色等随主题变化，但图标 data-uri 静态生成——此处按「浅色」
 * 提供真值，深色模式图标主色视觉上由 soft 底等其余元素补偿（与既有行为一致，
 * 避免引入动态重绘复杂度）。若后续需要深色图标，可在此按主题返回两套。
 */
export const ICON_COLOR_VARS: Record<string, string> = {
  '--color-primary': COLOR_PRIMARY_LIGHT,
  '--color-primary-dark': COLOR_PRIMARY_DARK_LIGHT,
  '--color-accent': COLOR_ACCENT_LIGHT,
  '--color-price': '#C0392B',
  '--color-error': '#E54D42',
  '--color-success': '#10B981',
  '--color-like': '#ff6b6b',
  '--color-like-soft': '#fff5f5',
  '--color-star': '#FFB400',
  '--color-star-empty': '#E8E0D8',
  '--text-primary': '#1C1917',
  '--text-secondary': '#6B625B',
  '--text-tertiary': '#A89E96',
  '--text-white': '#FFFFFF',
  '--badge-dark-text': '#FFFFFF',
  '--white': '#FFFFFF',
  'currentColor': '#1C1917',
}
