/**
 * 颜色取值真源（唯一）。
 *
 * 单一色相：中性灰阶承载底色 / 卡面 / 文字 / 描边，唯一彩色是主色 `brand`。
 * 改值只改这里，改完必须同步 `generated-colors.css` 的同键变量声明。
 * 无半透明叠加 / 渐变 / 投影；层级靠描边与圆角区分。
 */

export const COLOR_MAP = {
  /* ===== 文字 ===== */
  'text-title': '#1F2937',
  'text-body': '#6B7280',
  'text-disabled': '#9CA3AF',

  /* ===== 背景 / 边框 ===== */
  'bg-page': '#FFFFFF',
  'bg-card': '#FFFFFF',
  'bg-placeholder': '#F3F4F6',
  'border-color': '#E5E7EB',

  /* ===== 主色与反白色 ===== */
  brand: '#EA580C',
  'on-brand': '#FFFFFF',

  /* ===== 浮层遮罩（纯色不透明）===== */
  'overlay-scrim': '#F3F4F6',
} as const
