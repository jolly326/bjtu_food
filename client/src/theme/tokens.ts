/**
 * 品牌主题色单一事实源（token 常量）
 * ------------------------------------------------------------------
 * 背景：IconSvg.vue 的 SVG data-uri 图标无法解析 CSS var()，需真实色值，
 * 因此本文件作为「图标色」的唯一来源：改色只改这里，IconSvg 从中读取。
 * （页面级 CSS 变量在 App.vue 内直接声明，不依赖本文件；LIGHT_TOKENS /
 * DARK_TOKENS 此前试图统一页面 token，但未被任何模块引用，已从本文件移除。）
 *
 * 命名规范（design-system 三层 token）：
 *   primitive → semantic → component
 *   - 品牌色（hue≈15° 朱砂红 vermilion）：primary / on-primary / primary-soft
 *   - 辅助/语义色：accent（强调橙）、success / error / warning / price / star / like
 *   - 中性色：text-*（四档层级）
 *   - 每个 token 均有 浅色 / 深色 两值，结构对称，禁止浅深缺位。
 * ------------------------------------------------------------------
 */

/* ================= 品牌主色（Primitive） ================= */
/**
 * 产品决策（2026-08-16 拍板）：全站主题色由鲜橙调整为「朱砂红」，
 * 并收深、降低亮度（呼应食堂暖色场景，避免过亮刺眼）。
 *  - 浅色模式主色 = 朱砂红（收深）#9B2A1D（白字按钮可见，对比达标）
 *  - 深色模式主色 = 同色相提亮朱砂红 #C45A3C（深底醒目、白字 AA 达标）
 */
export const COLOR_PRIMARY_LIGHT = '#9B2A1D'
/** 浅色主色暗阶（hover / 深按） */
export const COLOR_PRIMARY_DARK_LIGHT = '#7A1F14'
/** 深色模式主色（珊瑚橙色相提亮，深底醒目；白字 AA 达标） */
export const COLOR_PRIMARY_DARK = '#C45A3C'
/** 深色主色暗阶 */
export const COLOR_PRIMARY_DARK_DARK = '#A8482E'
/** 主色上的文字：深浅均为白/暖白 */
export const COLOR_ON_PRIMARY_LIGHT = '#FFFFFF'
export const COLOR_ON_PRIMARY_DARK = '#FFFFFF'

/* ============ 主色表面上的文字（header / 页面 top 等大面积品牌色块） ============ */
export const COLOR_ON_PRIMARY_SURFACE_LIGHT = '#FFFFFF'
export const COLOR_ON_PRIMARY_SURFACE_DARK = '#F5EFEC'

/* ================= 辅助/语义色（Semantic） ================= */
/** 品牌强调色（热卖/热搜/新品统一走 accent，禁止另设 hot 重复定义） */
export const COLOR_ACCENT_LIGHT = '#C45A3C'
export const COLOR_ACCENT_DARK = '#C45A3C'

/** 价格红：暖珊瑚加深（浅色），深色提亮暖橙（与 error 区分） */
export const COLOR_PRICE_LIGHT = '#C45A3C'
export const COLOR_PRICE_DARK = '#E8A07E'

/** 错误 / 成功 / 喜欢（浅色基准 + 深色提亮） */
export const COLOR_ERROR_LIGHT = '#FF3B30'
export const COLOR_ERROR_DARK = '#FF6B61'
export const COLOR_SUCCESS_LIGHT = '#10B981'
export const COLOR_SUCCESS_DARK = '#34D399'
export const COLOR_LIKE_LIGHT = '#B53B2C'
export const COLOR_LIKE_DARK = '#D9695A'
/** 评分星：浅色金黄 / 深色提亮 */
export const COLOR_STAR_LIGHT = '#F5A623'
export const COLOR_STAR_DARK = '#FFC24B'
/** 空星：浅暖灰（避免低分时大片黑星）/ 深色深灰 */
export const COLOR_STAR_EMPTY_LIGHT = '#E5E5EA'
export const COLOR_STAR_EMPTY_DARK = '#3A3632'

/* ================= 中性色（Neutral） ================= */
/**
 * 文字四档层级（对比度：primary ≥7:1 / secondary ≥4.5:1 / tertiary ≥3:1）。
 * 2026-08-15 微调：中性灰统一带极轻暖调，与朱砂红主色更协调（非冷灰）。
 */
export const COLOR_TEXT_PRIMARY_LIGHT = '#1D1A18'
export const COLOR_TEXT_PRIMARY_DARK = '#F4F0EC'
export const COLOR_TEXT_SECONDARY_LIGHT = '#6E6964'
export const COLOR_TEXT_SECONDARY_DARK = '#B8B0A8'
export const COLOR_TEXT_TERTIARY_LIGHT = '#8F8A84'
export const COLOR_TEXT_TERTIARY_DARK = '#8E887F'

/**
 * IconSvg 图标色值表：SVG data-uri 无法解析 var()，须用真实色。
 * 图标色随当前主题：浅色用 LIGHT 真值，深色用 DARK 真值，
 * 保证深浅模式下图标颜色与页面 token 一致。
 */
export const ICON_COLOR_VARS: Record<string, Record<string, string>> = {
  light: {
    '--color-primary': COLOR_PRIMARY_LIGHT,
    '--color-primary-dark': COLOR_PRIMARY_DARK_LIGHT,
    '--color-on-primary': COLOR_ON_PRIMARY_LIGHT,
    '--color-on-primary-surface': COLOR_ON_PRIMARY_SURFACE_LIGHT,
    '--color-on-tab': COLOR_PRIMARY_LIGHT,
    '--color-accent': COLOR_ACCENT_LIGHT,
    '--color-price': COLOR_PRICE_LIGHT,
    '--color-error': COLOR_ERROR_LIGHT,
    '--color-success': COLOR_SUCCESS_LIGHT,
    '--color-like': COLOR_LIKE_LIGHT,
    '--color-like-soft': COLOR_LIKE_LIGHT,
    '--color-star': COLOR_STAR_LIGHT,
    '--color-star-empty': COLOR_STAR_EMPTY_LIGHT,
    '--color-cell-activity': '#1E5FCE',
    '--color-cell-feedback': '#0E9E6E',
    '--text-primary': COLOR_TEXT_PRIMARY_LIGHT,
    '--text-secondary': COLOR_TEXT_SECONDARY_LIGHT,
    '--text-tertiary': COLOR_TEXT_TERTIARY_LIGHT,
    '--text-white': '#FFFFFF',
    '--badge-dark-text': '#FFFFFF',
    '--white': '#FFFFFF',
    currentColor: COLOR_TEXT_PRIMARY_LIGHT,
  },
  dark: {
    '--color-primary': COLOR_PRIMARY_DARK,
    '--color-primary-dark': COLOR_PRIMARY_DARK_DARK,
    '--color-on-primary': COLOR_ON_PRIMARY_DARK,
    '--color-on-primary-surface': COLOR_ON_PRIMARY_SURFACE_DARK,
    '--color-on-tab': COLOR_PRIMARY_DARK,
    '--color-accent': COLOR_ACCENT_DARK,
    '--color-price': COLOR_PRICE_DARK,
    '--color-error': COLOR_ERROR_DARK,
    '--color-success': COLOR_SUCCESS_DARK,
    '--color-like': COLOR_LIKE_DARK,
    '--color-like-soft': COLOR_LIKE_DARK,
    '--color-star': COLOR_STAR_DARK,
    '--color-star-empty': COLOR_STAR_EMPTY_DARK,
    '--color-cell-activity': '#7FA8E8',
    '--color-cell-feedback': '#4CCF9A',
    '--text-primary': COLOR_TEXT_PRIMARY_DARK,
    '--text-secondary': COLOR_TEXT_SECONDARY_DARK,
    '--text-tertiary': COLOR_TEXT_TERTIARY_DARK,
    '--text-white': '#FFFFFF',
    '--badge-dark-text': '#FFFFFF',
    '--white': '#FFFFFF',
    currentColor: COLOR_TEXT_PRIMARY_DARK,
  },
}
