/**
 * 颜色取值真源（唯一）。
 *
 * 单一色相：中性灰阶承载底色 / 卡面 / 文字 / 描边，唯一彩色是主色 `brand`。
 * 双主题共用**同一套语义键**：`COLOR_MAP` = 浅色，`DARK_COLOR_MAP` = 深色（灰黑基调，取色对齐运营端）。
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
} as const

/**
 * 深色主题取值：语义键与浅色一一对应。
 * 底色压到近黑、卡面比底色抬一档（无投影，靠明度差分层），文字灰阶整体抬高，
 * 描边取中间灰；主色提亮一档以保证在近黑底上的辨识度，反白色保持不变。
 */
export const DARK_COLOR_MAP: Record<keyof typeof COLOR_MAP, string> = {
  /* ===== 文字 ===== */
  'text-title': '#E8EAED',
  'text-body': '#9CA2AC',
  'text-disabled': '#6F747E',

  /* ===== 背景 / 边框 ===== */
  'bg-page': '#17181B',
  'bg-card': '#202228',
  'bg-placeholder': '#262930',
  'border-color': '#3A3E46',

  /* ===== 主色与反白色 ===== */
  brand: '#F97316',
  'on-brand': '#FFFFFF',
}
