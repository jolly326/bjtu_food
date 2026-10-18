/**
 * 颜色取值真源（唯一）。
 *
 * 真源口径：色值只改这里；改完必须同步 `generated-colors.css` 的**同键变量声明**
 * （变量名以 `generated-colors.css` 为准，逐键一致）。
 * 已定稿：`--bg-page = #FFF9ED`（全站统一奶米色页面底色）；主色 `#B4531A`。
 * 消费清单见 docs/ui/client/设计变量.md；零消费的键不登记。
 */

export const COLOR_MAP = {
  /* ===== 主色（暖橙黄）===== */
  primary: '#B4531A',
  'primary-fill': '#B4531A',
  'primary-text': '#B4531A',
  'brown-deep': '#7B4F2A',
  'primary-soft': '#FFF3D6',

  /* ===== 功能色 ===== */
  error: '#C62828',
  warning: '#E67E22',
  'warning-soft': '#FFF8E1',

  /* ===== 文字四档（深棕标题 + 深炭灰正文 + 浅灰次要）===== */
  'text-title': '#44382e',
  'text-body': '#3D3A35',
  'text-tertiary': '#9A948C',
  'text-placeholder': '#C4BEB5',

  /* ===== 背景 / 边框 ===== */
  'bg-page': '#FFF9ED',
  'bg-card': '#FFFFFF',
  'bg-card-milky': 'rgba(255, 250, 243, 0.65)',
  'bg-placeholder': '#F2EBE1',
  'border-color': '#E8E3DE',
  'border-bold': '#CBC5BE',

  /* ===== 阴影 / 遮罩 ===== */
  /* 卡片阴影：微弱暖棕，仅靠阴影 + 描边呈现层次 */
  'module-shadow': '0 1rpx 3rpx rgba(180, 155, 125, 0.10)',
  /* 卡片栈单卡阴影：拉开卡与卡之间的层次 */
  'shadow-card-stack': '0 8rpx 24rpx rgba(68, 56, 46, 0.08)',
  'shadow-modal': '0 18rpx 54rpx rgba(0, 0, 0, 0.18)',
  'overlay-scrim': 'rgba(0, 0, 0, 0.4)',
  'overlay-panel-bg': '#FFFFFF',
} as const

export type ColorToken = keyof typeof COLOR_MAP
