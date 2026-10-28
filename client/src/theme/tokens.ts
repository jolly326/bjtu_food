/**
 * 颜色取值真源（唯一）。
 *
 * 真源口径：色值只改这里；改完必须同步 `generated-colors.css` 的**同键变量声明**
 * （变量名以 `generated-colors.css` 为准，逐键一致）。
 * 双主题：`COLOR_MAP` = 浅色组（默认，挂 `page`）；`DARK_COLOR_MAP` = 深色组
 * （挂页面根视图 `view[data-theme="dark"]`，级联整棵子树；机制见 docs/ui/client/设计变量.md §1.2）。
 *
 * 风格 = **Q 版动森**（暖奶油底 / 圆润厚实 / 软糖质感）：
 * 浅色组 `--bg-page = #FFF7E8`（暖奶油）、卡片奶白、主色暖橙 `#FF8A4C`；
 * 深色组为暖棕夜色（不是中性灰）—— 夜里的岛屿也要暖。
 */

export const COLOR_MAP = {
  /* ===== 主色（Q 版暖橙，动森「Nook 橙」语汇）===== */
  primary: '#FF8A4C',
  'primary-text': '#E8722F',
  'brown-deep': '#8A5A2B',
  'primary-soft': '#FFE3C7',

  /* ===== 文字（暖棕阶，非中性灰）===== */
  'text-title': '#5A3E2B',
  'text-body': '#6B5140',
  'text-tertiary': '#A08671',
  'text-secondary': '#B39C85',
  'text-placeholder': '#C9B49C',
  /* 主色底（按钮 / 弹窗主操作）上的文字色 */
  'text-on-primary': '#FFFFFF',

  /* ===== 背景 / 边框 ===== */
  /* 页面底色：暖奶油 */
  'bg-page': '#FFF7E8',
  /* 卡片底色：奶白，比页面亮一档 */
  'bg-card': '#FFFFFF',
  'bg-placeholder': '#F3E7D3',
  'border-color': '#EFE0C8',
  'border-bold': '#E2CDAB',

  /* ===== 阴影 / 遮罩（Q 版「贴纸」质感：实心错位 + 柔和外扩）===== */
  'shadow-card-stack': '0 8rpx 0 rgba(214,178,128,0.35), 0 12rpx 24rpx rgba(190,150,100,0.18)',
  'shadow-modal': '0 -6rpx 0 rgba(214,178,128,0.28), 0 -12rpx 32rpx rgba(190,150,100,0.22)',
  'overlay-scrim': 'rgba(90,62,43,0.32)',
  'overlay-panel-bg': '#FFFDF7',
} as const

/**
 * 深色组（**暖棕夜色**，Q 版动森的夜间岛屿：深可可底 + 奶油字；主色提亮为晚霞橙）。
 *
 * 取值映射：
 * 页面底 `#2A231C`（可可）｜ 卡片 `#372E25` ｜ 占位 `#443A2F`
 * 标题奶油 `#F7EBD9` ｜ 正文 `#E8D9C4` ｜ 次要 `#BCA892`
 * 主色晚霞橙 `#FFA76B` ｜ 文字色 `#FFC79B`
 */
export const DARK_COLOR_MAP = {
  /* ===== 主色（暗底提亮：晚霞橙）===== */
  primary: '#FFA76B',
  'primary-text': '#FFC79B',
  'brown-deep': '#C8AC8E',
  'primary-soft': '#4A3826',

  /* ===== 文字（奶油阶）===== */
  'text-title': '#F7EBD9',
  'text-body': '#E8D9C4',
  'text-tertiary': '#BCA892',
  'text-secondary': '#A08D78',
  'text-placeholder': '#7E6C59',
  'text-on-primary': '#3A2A1A',

  /* ===== 背景 / 边框 ===== */
  'bg-page': '#2A231C',
  'bg-card': '#372E25',
  'bg-placeholder': '#443A2F',
  'border-color': 'rgba(255,255,255,0.14)',
  'border-bold': 'rgba(255,255,255,0.24)',

  /* ===== 阴影 / 遮罩（深色下压深，保留贴纸错位的「厚」）===== */
  'shadow-card-stack': '0 8rpx 0 rgba(0,0,0,0.32), 0 12rpx 24rpx rgba(0,0,0,0.28)',
  'shadow-modal': '0 -6rpx 0 rgba(0,0,0,0.30), 0 -12rpx 32rpx rgba(0,0,0,0.34)',
  'overlay-scrim': 'rgba(0,0,0,0.55)',
  'overlay-panel-bg': '#372E25',
} as const

export type ColorToken = keyof typeof COLOR_MAP
