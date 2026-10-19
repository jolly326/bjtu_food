/**
 * 颜色取值真源（唯一）。
 *
 * 真源口径：色值只改这里；改完必须同步 `generated-colors.css` 的**同键变量声明**
 * （变量名以 `generated-colors.css` 为准，逐键一致）。
 * 双主题：`COLOR_MAP` = 浅色组（默认，挂 `page`）；`DARK_COLOR_MAP` = 深色组
 * （挂页面根视图 `view[data-theme="dark"]`，级联整棵子树；机制见 docs/ui/client/设计变量.md §1.2）。
 * 浅色组：`--bg-page = #FFFAF3`（全站统一暖白页面底色）；主色 `#B4531A`。
 * 消费清单见 docs/ui/client/设计变量.md；零消费的键不登记。
 */

export const COLOR_MAP = {
  /* ===== 主色（暖橙黄）===== */
  primary: '#B4531A',
  'primary-text': '#B4531A',
  'brown-deep': '#7B4F2A',
  'primary-soft': '#FFF3D6',

  /* ===== 文字五档（深棕标题 + 深炭灰正文 + 浅棕灰辅助 + 弱化 + 装饰水印）===== */
  'text-title': '#44382e',
  'text-body': '#3D3A35',
  'text-tertiary': '#8a7c70',
  'text-secondary': '#888888',
  'text-decor': '#E6DECD',
  'text-placeholder': '#C4BEB5',
  /* 主色底（按钮 / 弹窗主操作）上的文字色 */
  'text-on-primary': '#FFFFFF',

  /* ===== 背景 / 边框 ===== */
  /* 页面底色：纯白 */
  'bg-page': '#FFFFFF',
  /* 卡片底色：与页面同色（纯白）—— 层次只由弱阴影与描边承担 */
  'bg-card': '#FFFFFF',
  'bg-placeholder': '#F2EBE1',
  'border-color': '#E8E3DE',
  'border-bold': '#CBC5BE',

  /* ===== 阴影 / 遮罩 ===== */
  /* 卡片族统一：两层极淡软阴影，模拟轻微悬浮，没有硬边 */
  'shadow-card-stack': '0 8rpx 24rpx rgba(140, 120, 100, 0.06), 0 2rpx 8rpx rgba(140, 120, 100, 0.03)',
  /* 堆叠后方层阴影：透明度继续降低，实现上浮景深（中层 / 下层两档） */
  'shadow-card-back': '0 6rpx 16rpx rgba(140, 120, 100, 0.04), 0 1rpx 6rpx rgba(140, 120, 100, 0.02)',
  'shadow-card-back-far': '0 4rpx 12rpx rgba(140, 120, 100, 0.025), 0 1rpx 4rpx rgba(140, 120, 100, 0.015)',
  'shadow-modal': '0 18rpx 54rpx rgba(0, 0, 0, 0.18)',
  /* 内阴影：纸片卡顶部吊孔「打孔下沉」的层级感 */
  'shadow-inset': 'inset 0 2rpx 6rpx rgba(68, 56, 46, 0.16)',
  'overlay-scrim': 'rgba(0, 0, 0, 0.4)',
  'overlay-panel-bg': '#FFFFFF',
} as const

/**
 * 深色组（暖黑棕底 + 暖白字，低调饱和度；键与浅色组完全一致，仅换值）。
 * 阴影在深色下为「压深」语义（暗底上装饰性暖棕阴影不可见），禁高对比纯黑装饰性阴影。
 */
export const DARK_COLOR_MAP = {
  /* ===== 主色（暗底提亮保对比）===== */
  primary: '#E07E38',
  'primary-text': '#F0995A',
  'brown-deep': '#BFAE93',
  'primary-soft': '#3B2A1B',

  /* ===== 文字五档（暖白标题 + 暖浅灰正文 + 中暖灰次要 + 弱化辅助 + 装饰水印）===== */
  'text-title': '#F0E7DA',
  'text-body': '#D8CEC0',
  'text-tertiary': '#93887A',
  'text-secondary': '#8C8375',
  'text-decor': '#3A3229',
  'text-placeholder': '#5E564B',
  'text-on-primary': '#FFFFFF',

  /* ===== 背景 / 边框 ===== */
  'bg-page': '#1A1410',
  /* 深色卡片底色：比页面底色再深一点点的奶黑棕 */
  'bg-card': '#241c15',
  'bg-placeholder': '#332B22',
  'border-color': '#3D3429',
  'border-bold': '#4E4536',

  /* ===== 阴影 / 遮罩（压深语义）===== */
  'shadow-card-stack': '0 8rpx 24rpx rgba(0, 0, 0, 0.38)',
  /* 堆叠后方层阴影：深底下继续压深（两档） */
  'shadow-card-back': '0 6rpx 16rpx rgba(0, 0, 0, 0.28)',
  'shadow-card-back-far': '0 4rpx 12rpx rgba(0, 0, 0, 0.2)',
  'shadow-modal': '0 -8rpx 32rpx rgba(0, 0, 0, 0.45)',
  'shadow-inset': 'inset 0 2rpx 6rpx rgba(0, 0, 0, 0.45)',
  'overlay-scrim': 'rgba(0, 0, 0, 0.6)',
  'overlay-panel-bg': '#28211A',
} as const

export type ColorToken = keyof typeof COLOR_MAP
