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
} as const

export const IMAGE = {
  /** 磁贴缩略图限宽（px）：3 列磁贴在 3x 屏约 318px */
  TILE_THUMB_WIDTH: 400,
  /** 展开卡大图缩略图限宽（px）：整行 16:9 大图在 3x 屏约 933px */
  CARD_THUMB_WIDTH: 800,
} as const

export const STORAGE = {
  /** 学生端 token 存储键 */
  STUDENT_TOKEN: 'student_token',
} as const
