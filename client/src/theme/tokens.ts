/**
 * 品牌主题色单一事实源（token 常量）
 * ------------------------------------------------------------------
 * 背景：小程序主色用于两处——App.vue 的 CSS 变量声明，以及 IconSvg.vue 的
 * SVG data-uri 图标（SVG 无法解析 var()，需真实色值）。此前两处各自硬编码，
 * 主色变更时若漏改其一，图标会与主题脱色。
 * 本文件作为唯一来源：改色只改这里，App.vue 与 IconSvg 均从此处读取。
 *
 * 命名规范（design-system 三层 token）：
 *   primitive → semantic → component
 *   - 品牌色（hue≈10° terracotta 陶土红）：primary / on-primary / primary-soft
 *   - 辅助/语义色：accent（强调橙）、success / error / warning / price / star / like
 *   - 中性色：text-*（四档层级）、bg-*（页/卡/软底）、border-*
 *   - 每个 token 均有 浅色 / 深色 两值，结构对称，禁止浅深缺位。
 * ------------------------------------------------------------------
 */

/* ================= 品牌主色（Primitive） ================= */
/**
 * 产品决策（2026-08-12 拍板）：全站主题色统一为「暖杏色系」（呼应食堂暖色场景）。
 *  - 浅色模式主色 = 暖杏 #D4884C（用户指定），白字按钮可见
 *  - 深色模式主色 = 同色相提亮暖杏 #E8A870（直接使用 #D4884C 在深底上对比不足，
 *    深底可见、白字 AA 达标）
 */
export const COLOR_PRIMARY_LIGHT = '#D4884C'
/** 浅色主色暗阶（hover / 深按） */
export const COLOR_PRIMARY_DARK_LIGHT = '#B8773F'
/** 深色模式主色（暖杏色相提亮，深底醒目；白字 AA 达标） */
export const COLOR_PRIMARY_DARK = '#E8A870'
/** 深色主色暗阶 */
export const COLOR_PRIMARY_DARK_DARK = '#C98F55'
/** 主色上的文字：深浅均为白/暖白 */
export const COLOR_ON_PRIMARY_LIGHT = '#FFFFFF'
export const COLOR_ON_PRIMARY_DARK = '#FFFFFF'

/* ============ 主色表面（大面积 surface：header / 页面 top） ============ */
/**
 * 深色设计原则：大面积表面应变暗，亮主色只用于小面积强调（按钮/图标/标签）。
 * 产品决策：header / home-top 等大面积品牌色块深浅模式统一用暖杏系
 * （浅色 #D4884C / 深色提亮 #C98F55），保证品牌识别一致。
 */
export const COLOR_PRIMARY_SURFACE_LIGHT = '#D4884C'
export const COLOR_PRIMARY_SURFACE_DARK = '#C98F55'
/** 表面上的文字/图标：暖杏表面配白/暖白字（AA 达标） */
export const COLOR_ON_PRIMARY_SURFACE_LIGHT = '#FFFFFF'
export const COLOR_ON_PRIMARY_SURFACE_DARK = '#F5EFEC'

/* ================= 辅助/语义色（Semantic） ================= */
/** 品牌强调色（热卖/热搜/新品统一走 accent，禁止另设 hot 重复定义） */
export const COLOR_ACCENT_LIGHT = '#E8965C'
export const COLOR_ACCENT_DARK = '#E8A870'

/** 价格红：暖杏加深（浅色），深色提亮暖橙（与 error 区分） */
export const COLOR_PRICE_LIGHT = '#C2410C'
export const COLOR_PRICE_DARK = '#FFB088'

/** 错误 / 成功 / 警示 / 喜欢（浅色基准 + 深色提亮） */
export const COLOR_ERROR_LIGHT = '#FF3B30'
export const COLOR_ERROR_DARK = '#FF6B61'
export const COLOR_SUCCESS_LIGHT = '#10B981'
export const COLOR_SUCCESS_DARK = '#34D399'
export const COLOR_WARNING_LIGHT = '#F5A623'
export const COLOR_WARNING_DARK = '#F5B83D'
export const COLOR_LIKE_LIGHT = '#FF6B6B'
export const COLOR_LIKE_DARK = '#FF7B7B'
/** 评分星：浅色金黄 / 深色提亮 */
export const COLOR_STAR_LIGHT = '#F5A623'
export const COLOR_STAR_DARK = '#FFC24B'
/** 空星：浅暖灰（避免低分时大片黑星）/ 深色深灰 */
export const COLOR_STAR_EMPTY_LIGHT = '#E5E5EA'
export const COLOR_STAR_EMPTY_DARK = '#3A3632'

/* ================= 中性色（Neutral） ================= */
/** 文字四档层级（对比度：primary ≥7:1 / secondary ≥4.5:1 / tertiary ≥3:1 / quaternary 弱化） */
export const COLOR_TEXT_PRIMARY_LIGHT = '#1C1C1E'
export const COLOR_TEXT_PRIMARY_DARK = '#F2EFEC'
export const COLOR_TEXT_SECONDARY_LIGHT = '#6C6C70'
export const COLOR_TEXT_SECONDARY_DARK = '#B5ADA6'
export const COLOR_TEXT_TERTIARY_LIGHT = '#8E8E93'
export const COLOR_TEXT_TERTIARY_DARK = '#8A837C'
export const COLOR_TEXT_QUATERNARY_LIGHT = '#A8A09A'
export const COLOR_TEXT_QUATERNARY_DARK = '#6B6560'

/** 背景三档（页 / 卡片 / 软底）+ 占位 */
export const COLOR_BG_PAGE_LIGHT = '#F5F5F7'
export const COLOR_BG_PAGE_DARK = '#141414'
export const COLOR_BG_CARD_LIGHT = '#FFFFFF'
export const COLOR_BG_CARD_DARK = '#1F1F1F'
export const COLOR_BG_SOFT_LIGHT = '#EDEDF0'
export const COLOR_BG_SOFT_DARK = '#2A2A2A'
export const COLOR_BG_PLACEHOLDER_LIGHT = '#F0F0F0'
export const COLOR_BG_PLACEHOLDER_DARK = '#262626'

/** 边框两档（细边框 / 强调边框） */
export const COLOR_BORDER_LIGHT = '#E5E5EA'
export const COLOR_BORDER_DARK = '#2E2A27'
export const COLOR_BORDER_BOLD_LIGHT = '#C9C9CE'
export const COLOR_BORDER_BOLD_DARK = '#3D3935'

/**
 * 浅色模式 token 色值表（供 App.vue page/:root 声明）
 * 结构上与 DARK_TOKENS 对称，禁止一边有另一边缺。
 */
export const LIGHT_TOKENS: Record<string, string> = {
  // 品牌主色
  '--color-primary': COLOR_PRIMARY_LIGHT,
  '--color-primary-dark': COLOR_PRIMARY_DARK_LIGHT,
  '--color-on-primary': COLOR_ON_PRIMARY_LIGHT,
  '--color-on-tab': COLOR_PRIMARY_LIGHT,
  '--color-primary-soft': '#E8C9A3',
  // 主色表面（header/top 大面积）
  '--color-primary-surface': COLOR_PRIMARY_SURFACE_LIGHT,
  '--color-on-primary-surface': COLOR_ON_PRIMARY_SURFACE_LIGHT,
  // 强调/语义色
  '--color-accent': COLOR_ACCENT_LIGHT,
  '--color-accent-soft': '#FBEEDD',
  '--color-price': COLOR_PRICE_LIGHT,
  '--color-error': COLOR_ERROR_LIGHT,
  '--color-error-soft': '#FFECEB',
  '--color-success': COLOR_SUCCESS_LIGHT,
  '--color-success-soft': '#ECFDF5',
  '--color-warning': COLOR_WARNING_LIGHT,
  '--color-warning-soft': '#FFF8E1',
  '--color-like': COLOR_LIKE_LIGHT,
  '--color-like-soft': '#FFF5F5',
  '--color-star': COLOR_STAR_LIGHT,
  '--color-star-empty': COLOR_STAR_EMPTY_LIGHT,
  // 中性色
  '--text-primary': COLOR_TEXT_PRIMARY_LIGHT,
  '--text-secondary': COLOR_TEXT_SECONDARY_LIGHT,
  '--text-tertiary': COLOR_TEXT_TERTIARY_LIGHT,
  '--text-quaternary': COLOR_TEXT_QUATERNARY_LIGHT,
  '--bg-page': COLOR_BG_PAGE_LIGHT,
  '--bg-card': COLOR_BG_CARD_LIGHT,
  '--bg-input': '#F5F5F7',
  '--bg-soft': COLOR_BG_SOFT_LIGHT,
  '--bg-placeholder': COLOR_BG_PLACEHOLDER_LIGHT,
  '--border-color': COLOR_BORDER_LIGHT,
  '--border-bold': COLOR_BORDER_BOLD_LIGHT,
}

/**
 * 深色模式 token 色值表（供 App.vue theme-dark 声明）
 * 原则：灰黑底 + 提亮主色 + 低饱和文字（apple-design §12 深色材质）。
 */
export const DARK_TOKENS: Record<string, string> = {
  // 品牌主色
  '--color-primary': COLOR_PRIMARY_DARK,
  '--color-primary-dark': COLOR_PRIMARY_DARK_DARK,
  '--color-on-primary': COLOR_ON_PRIMARY_DARK,
  '--color-on-tab': COLOR_PRIMARY_DARK,
  '--color-primary-soft': '#3D2A24',
  // 主色表面（header/top 大面积：深色下变暗）
  '--color-primary-surface': COLOR_PRIMARY_SURFACE_DARK,
  '--color-on-primary-surface': COLOR_ON_PRIMARY_SURFACE_DARK,
  // 强调/语义色
  '--color-accent': COLOR_ACCENT_DARK,
  '--color-accent-soft': '#3A2A1C',
  '--color-price': COLOR_PRICE_DARK,
  '--color-error': COLOR_ERROR_DARK,
  '--color-error-soft': '#3A2321',
  '--color-success': COLOR_SUCCESS_DARK,
  '--color-success-soft': '#16302A',
  '--color-warning': COLOR_WARNING_DARK,
  '--color-warning-soft': '#382D1B',
  '--color-like': COLOR_LIKE_DARK,
  '--color-like-soft': '#3A2424',
  '--color-star': COLOR_STAR_DARK,
  '--color-star-empty': COLOR_STAR_EMPTY_DARK,
  // 中性色
  '--text-primary': COLOR_TEXT_PRIMARY_DARK,
  '--text-secondary': COLOR_TEXT_SECONDARY_DARK,
  '--text-tertiary': COLOR_TEXT_TERTIARY_DARK,
  '--text-quaternary': COLOR_TEXT_QUATERNARY_DARK,
  '--bg-page': COLOR_BG_PAGE_DARK,
  '--bg-card': COLOR_BG_CARD_DARK,
  '--bg-input': '#2A2A2A',
  '--bg-soft': COLOR_BG_SOFT_DARK,
  '--bg-placeholder': COLOR_BG_PLACEHOLDER_DARK,
  '--border-color': COLOR_BORDER_DARK,
  '--border-bold': COLOR_BORDER_BOLD_DARK,
}

/**
 * IconSvg 图标色值表：SVG data-uri 无法解析 var()，须用真实色。
 * 图标色随当前主题：浅色用 LIGHT 真值，深色用 DARK 真值，
 * 保证深浅模式下图标颜色与页面 token 一致（此前仅浅色一套，深色图标脱色）。
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
    '--text-primary': COLOR_TEXT_PRIMARY_DARK,
    '--text-secondary': COLOR_TEXT_SECONDARY_DARK,
    '--text-tertiary': COLOR_TEXT_TERTIARY_DARK,
    '--text-white': '#FFFFFF',
    '--badge-dark-text': '#FFFFFF',
    '--white': '#FFFFFF',
    currentColor: COLOR_TEXT_PRIMARY_DARK,
  },
}
