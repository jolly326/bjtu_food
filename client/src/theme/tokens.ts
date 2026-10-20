/**
 * 颜色取值真源（唯一）。
 *
 * 真源口径：色值只改这里；改完必须同步 `generated-colors.css` 的**同键变量声明**
 * （变量名以 `generated-colors.css` 为准，逐键一致）。
 * 双主题：`COLOR_MAP` = 浅色组（默认，挂 `page`）；`DARK_COLOR_MAP` = 深色组
 * （挂页面根视图 `view[data-theme="dark"]`，级联整棵子树；机制见 docs/ui/client/设计变量.md §1.2）。
 * 浅色组：`--bg-page = #FFFFFF`（纯白页面底）、`--bg-card = #FAFAFA`；主色 `#B4531A`（暖橙）。
 * 深色组：中性灰阶（DeepSeek 对话页夜间模式）—— `bg #1E1E1E` / `card #262626` / 主色仍为品牌暖橙。
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
  'bg-card': '#FAFAFA',
  'bg-placeholder': '#F2EBE1',
  'border-color': '#E8E3DE',
  'border-bold': '#CBC5BE',

  /* ===== 阴影 / 遮罩 ===== */
  /* 卡片族统一：两层极淡软阴影，模拟轻微悬浮，没有硬边 */
  'shadow-card-stack': '0 8rpx 24rpx rgba(140, 120, 100, 0.06), 0 2rpx 8rpx rgba(140, 120, 100, 0.03)',
  /* 堆叠后方层阴影：透明度继续降低，实现上浮景深（中层 / 下层两档） */
  'shadow-card-back': '0 6rpx 16rpx rgba(140, 120, 100, 0.04), 0 1rpx 6rpx rgba(140, 120, 100, 0.02)',
  'shadow-card-back-far': '0 4rpx 12rpx rgba(140, 120, 100, 0.025), 0 1rpx 4rpx rgba(140, 120, 100, 0.015)',
  /* 弹层：极弱双层弥散（暖灰），禁高对比硬阴影 */
  'shadow-modal': '0 16rpx 44rpx rgba(140, 120, 100, 0.12), 0 2rpx 10rpx rgba(140, 120, 100, 0.06)',
  /* 内阴影：纸片卡顶部吊孔「打孔下沉」的层级感 */
  'shadow-inset': 'inset 0 2rpx 6rpx rgba(68, 56, 46, 0.16)',
  'overlay-scrim': 'rgba(0, 0, 0, 0.4)',
  'overlay-panel-bg': '#FFFFFF',
} as const

/**
 * 深色组（**中性灰阶**，对齐 DeepSeek 对话网页端夜间模式：无蓝紫倾向的纯灰底 + 浅灰字；
 * 键与浅色组完全一致，仅换值）。
 *
 * 取值映射（DeepSeek chat dark → 本项目 token）：
 * 页面底 ≈ #1E1E1E → --bg-page ｜ 面板 / 卡片 ≈ #262626 → --bg-card
 * 悬停 / 占位 ≈ #2F2F2F → --bg-placeholder ｜ 正文 #ECECEC → --text-title
 * 次要 ≈ #A0A0A0 → --text-tertiary ｜ 弱化 ≈ #8A8A8A → --text-secondary
 * 边框 rgba(255,255,255,.10) → --border-color ｜ .18 → --border-bold
 *
 * 🔴 主色仍是品牌暖橙（暗底提亮），**不引入蓝紫**；层级由色阶（page → card → placeholder）承担。
 */
export const DARK_COLOR_MAP = {
  /* ===== 主色（品牌暖橙，暗底提亮保对比）===== */
  primary: '#E07E38',
  'primary-text': '#F0995A',
  'brown-deep': '#A3A3A3',
  'primary-soft': '#3A2A1E',

  /* ===== 文字五档（中性浅灰标题 + 正文 + 次要 + 弱化 + 装饰水印）===== */
  'text-title': '#ECECEC',
  'text-body': '#CFCFCF',
  'text-tertiary': '#A0A0A0',
  'text-secondary': '#8A8A8A',
  'text-decor': '#2F2F2F',
  'text-placeholder': '#6B6B6B',
  'text-on-primary': '#FFFFFF',

  /* ===== 背景 / 边框（中性灰色阶：页面 → 卡片 → 占位）===== */
  'bg-page': '#1E1E1E',
  'bg-card': '#262626',
  'bg-placeholder': '#2F2F2F',
  'border-color': 'rgba(255, 255, 255, 0.1)',
  'border-bold': 'rgba(255, 255, 255, 0.18)',

  /* ===== 阴影 / 遮罩（纯黑压深语义）===== */
  'shadow-card-stack': '0 8rpx 24rpx rgba(0, 0, 0, 0.35)',
  /* 堆叠后方层阴影：深底下继续压深（两档） */
  'shadow-card-back': '0 6rpx 16rpx rgba(0, 0, 0, 0.26)',
  'shadow-card-back-far': '0 4rpx 12rpx rgba(0, 0, 0, 0.18)',
  'shadow-modal': '0 -8rpx 32rpx rgba(0, 0, 0, 0.5)',
  'shadow-inset': 'inset 0 2rpx 6rpx rgba(0, 0, 0, 0.5)',
  'overlay-scrim': 'rgba(0, 0, 0, 0.6)',
  'overlay-panel-bg': '#262626',
} as const

export type ColorToken = keyof typeof COLOR_MAP
