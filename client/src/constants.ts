/**
 * 客户端全局魔法数字与常量（单一真源）。
 * 尺寸 / 时长 / 阈值 / storage 键一律在此登记（`@/constants` 引入），组件与页面不得另行内联同名数字。
 */

export const REQUEST = {
  /** 请求超时（ms） */
  TIMEOUT_MS: 15000,
  /** 窗口 resize 防抖（ms） */
  RESIZE_DEBOUNCE_MS: 120,
} as const

export const FEEDBACK = {
  /** 正文最大字数 */
  MAX_LEN: 500,
} as const

export const SEARCH_DOCK = {
  /** 无点击 / 输入多久后进入待机淡出态（ms） */
  IDLE_MS: 5000,
  /** 输入框聚焦时与键盘的间距（px，平台单位） */
  CURSOR_SPACING: 24,
} as const

export const IMAGE = {
  /** 结果卡封面缩略图限宽（px）：1 列 16:9 满宽封面在 3x 屏约 1000px */
  RESULT_THUMB_WIDTH: 1000,
  /** 缩略图兜底限宽（px）：宿主未指定宽度时使用 */
  THUMB_WIDTH: 400,
  /** 缩略图 webp 质量档（数据万象 quality 参数） */
  THUMB_QUALITY: 80,
} as const

export const RESULT = {
  /** 描述常驻 2 行的字数阈值，超过则提供「展开」入口 */
  DESCRIPTION_PREVIEW_CHARS: 72,
} as const

export const STORAGE = {
  /** 学生端 token 存储键 */
  STUDENT_TOKEN: 'student_token',
  /** 主题模式存储键（`light` / `dark`） */
  THEME: 'theme_mode',
} as const

export const NAV = {
  /** 微信胶囊行兜底高度（px）：运行时测量不可用时使用 */
  DEFAULT_HEADER_HEIGHT_PX: 44,
  /** 页头右侧为原生胶囊预留的兜底宽度（px） */
  DEFAULT_RIGHT_RESERVE_PX: 100,
  /** 页头内容与原生胶囊之间的呼吸间距（px） */
  CAPSULE_GAP_PX: 8,
  /** 状态栏前景色：自定义导航下由平台 API 取反，仅接受 #ffffff / #000000（🔴 必须小写，大写被拒） */
  STATUS_BAR_FRONT_LIGHT: '#000000',
  STATUS_BAR_FRONT_DARK: '#ffffff',
  /** 状态栏背景色：平台 API 必填（自定义导航下不渲染，缺省即报 parameter error），与 --bg-page 同值 */
  STATUS_BAR_BG_LIGHT: '#ffffff',
  STATUS_BAR_BG_DARK: '#17181b',
} as const
