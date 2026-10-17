/**
 * 颜色取值真源（唯一）。
 *
 * 真源口径：色值只改这里；改完必须同步 `generated-colors.css` 的**同键变量声明**
 * （变量名以 `generated-colors.css` 为准，逐键一致）。
 * 已定稿：`--bg-page = #FFFDFE`（全站统一页面底色）；主色 `#B4531A`。
 * 消费清单见 docs/ui/client/设计变量.md；零消费的键不登记。
 */

export const COLOR_MAP = {
  /* ===== 主色（暖橙黄）===== */
  primary: '#B4531A',
  'primary-fill': '#B4531A',
  'primary-text': '#B4531A',
  'primary-bright': '#B4531A',
  'primary-orange': '#E67E22',
  'primary-yellow': '#FFD166',
  'orange-deep': '#D35400',
  'brown-deep': '#7B4F2A',
  'on-primary': '#FFFFFF',
  'primary-soft': '#FFF3D6',

  /* ===== 强调 ===== */
  accent: '#B4531A',
  'accent-soft': '#FAE0CE',

  /* ===== 功能色 ===== */
  error: '#C62828',
  'error-soft': '#FFECEB',
  warning: '#E67E22',
  'warning-soft': '#FFF8E1',
  success: '#2E7D32',
  info: '#1565C0',

  /* ===== 价格 ===== */
  price: '#B4531A',

  /* ===== 文字四档 ===== */
  'text-title': '#7B4F2A',
  'text-body': '#7F6A55',
  'text-tertiary': '#7F6A55',
  'text-placeholder': '#B5A594',
  'text-white': '#FFFFFF',

  /* ===== 背景 / 边框 ===== */
  'bg-card': '#FFFFFF',
  'bg-input': '#F7F5F2',
  'bg-soft': '#EDE9E5',
  'bg-placeholder': '#F2EBE1',
  'border-color': '#E8E3DE',
  'border-bold': '#CBC5BE',

  /* ===== 阴影 / 遮罩 ===== */
  /* 卡片阴影：微弱暖棕，仅靠阴影 + 描边呈现层次 */
  'module-shadow': '0 1rpx 3rpx rgba(180, 155, 125, 0.10)',
  'shadow-modal': '0 18rpx 54rpx rgba(0, 0, 0, 0.18)',
  'overlay-scrim': 'rgba(0, 0, 0, 0.4)',
  'overlay-panel-bg': '#FFFFFF',
} as const

export type ColorToken = keyof typeof COLOR_MAP
