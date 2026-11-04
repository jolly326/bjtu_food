/**
 * 颜色取值真源（唯一）。
 *
 * 单主题（奶米纸色）：色值只改这里，改完必须同步 `generated-colors.css` 的**同键变量声明**。
 *
 * 动森风移动端规范：奶米 `#FFF7E8`、卡片奶白 `#FFFDF7`、主文字深棕 `#44382E`、
 * 辅助浅棕 `#8C7B6B`、置灰 / 三级 `#9F927D`、浅杏 `#F9E9D8`。
 * 层级表达：仅 `--shadow-soft` / `--shadow-up` 极弱弥散阴影；🔴 无硬阴影 / 渐变 / 3D 堆叠。
 */

export const COLOR_MAP = {
  /* ===== 文字（深棕 / 辅助浅棕 / 三级）===== */
  'text-title': '#44382E',
  'text-body': '#8C7B6B',
  'text-disabled': '#9F927D',

  /* ===== 背景 / 边框 ===== */
  'bg-page': '#FFF7E8',
  'bg-card': '#FFFDF7',
  'bg-placeholder': '#F3E7D3',
  'border-color': '#E6D8C6',
  /* 浅杏胶囊底：软胶囊按钮（反馈 / 导航）的浅杏承载底 */
  'pill-bg': '#F9E9D8',

  /* ===== 低透明度奶色蒙层 + 极弱弥散阴影 ===== */
  'overlay-scrim': 'rgba(255,247,232,0.60)',
  'overlay-panel-bg': '#FFF7E8',
  'card-divider': '#D9C7B2',
  'shadow-soft': '0 2rpx 10rpx rgba(68, 56, 46, 0.08)',
  'shadow-up': '0 -2rpx 12rpx rgba(68, 56, 46, 0.10)',
} as const
