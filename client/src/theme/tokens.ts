/**
 * 颜色取值真源（唯一）。
 *
 * 真源口径：色值只改这里；改完必须同步 `generated-colors.css` 的**同键变量声明**
 * （变量名以 `generated-colors.css` 为准，逐键一致）。
 * 双主题：`COLOR_MAP` = 浅色组（默认，挂 `page`）；`DARK_COLOR_MAP` = 深色组
 * （挂页面根视图 `view[data-theme="dark"]`，级联整棵子树；机制见 docs/ui/client/设计变量.md §1.2）。
 *
 * 浅色组 = 动森风移动端规范：奶米纸色 `#FFFAF3`、深棕 `#44382E`、浅棕 `#8C7B6B`。
 * 可读性修正：`#8C7B6B` 在奶米底仅 3.92:1、浅杏胶囊上 3.43:1，故辅助正文压深到
 * `#75665A`（奶米底 5.31:1 / 浅杏底 4.65:1）；`#8C7B6B` 保留给弱提示、箭头与装饰。
 * 深色组为**低饱和夜色**（深灰蓝 `#2B3A4A` + 冷调奶白字）—— 🔴 不做纯黑，夜里是低饱和夜色。
 * 层级表达：**全局无阴影** —— 只靠底色（胶囊）、描边、圆角区分；置灰用暖灰棕 `#B8A99C`。
 * 双主题共用同一套形态语言（圆角 / 描边宽度 / 动效），只换色温与明度。
 */

export const COLOR_MAP = {
  /* ===== 主色（暖橙保留给非 sheet 的品牌 CTA；🔴 品牌保留 token，不按「零消费即删」删除）===== */
  primary: '#FF8A4C',

  /* ===== 文字（规范深棕 / 可读浅棕阶）===== */
  'text-title': '#44382E',
  'text-body': '#75665A',
  'color-muted': '#8C7B6B',
  /* 置灰 / 禁用：暖灰棕（不是冷灰，避免破坏暖调） */
  'text-disabled': '#B8A99C',

  /* ===== 背景 / 边框 ===== */
  /* 页面底色：奶米纸色 */
  'bg-page': '#FFFAF3',
  /* 卡片底色：奶白，比页面亮一档 */
  'bg-card': '#FFFDF7',
  'bg-placeholder': '#F3E7D3',
  'border-color': '#E6D8C6',


  /* ===== 遮罩（无阴影：层级靠底色 / 描边 / 圆角区分）===== */
  'overlay-scrim': 'rgba(90,62,43,0.32)',
  'overlay-panel-bg': '#FFFAF3',
  'overlay-sheet-scrim': 'rgba(255,250,243,0.72)',
  /* 主操作胶囊：杏色加深一档 */
  /* 胶囊 / 气泡描边：深棕 */
  'sheet-pill-border': '#44382E',
  'sheet-divider': '#D9C7B2',
} as const

/**
 * 深色组（**低饱和夜色**：深灰蓝底 + 冷调奶白字；主色提亮为晚霞橙）。
 *
 * 取值映射：
 * 页面底 `#2B3A4A`（低饱和夜色，非纯黑）｜ 卡片 `#354758` ｜ 占位 `#3E5265`
 * 标题冷调奶白 `#EDEFF2` ｜ 正文 `#C9D0D6` ｜ 次要 `#A8B2BA`
 * 主色晚霞橙 `#FFA76B`
 * 描边族 = 冷调奶白半透明；卡片「微弱夜光边缘」由描边承担（🔴 不引入阴影）。
 */
export const DARK_COLOR_MAP = {
  /* ===== 主色（暗底提亮：晚霞橙）===== */
  primary: '#FFA76B',

  /* ===== 文字（冷调奶白阶）===== */
  'text-title': '#EDEFF2',
  'text-body': '#C9D0D6',
  'color-muted': '#A8B2BA',
  'text-disabled': '#7C8A96',

  /* ===== 背景 / 边框 ===== */
  'bg-page': '#2B3A4A',
  'bg-card': '#354758',
  'bg-placeholder': '#3E5265',
  'border-color': 'rgba(237,239,242,0.22)',


  /* ===== 遮罩 ===== */
  'overlay-scrim': 'rgba(12,18,24,0.55)',
  'overlay-panel-bg': '#354758',
  'overlay-sheet-scrim': 'rgba(43,58,74,0.72)',
  /* 暗底上深棕描边不可见 ⇒ 提亮为冷调奶白描边 */
  'sheet-pill-border': 'rgba(237,239,242,0.55)',
  'sheet-divider': 'rgba(237,239,242,0.22)',
} as const
