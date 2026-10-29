/**
 * 客户端全局魔法数字与常量（单一真源）。
 * 尺寸 / 时长 / 阈值 / storage 键一律在此登记（`@/constants` 引入），组件与页面不得另行内联同名数字。
 */

export const SHEET = {
  /** 反馈选项每页显示数量 */
  FEEDBACK_PAGE_SIZE: 3,
  /** 点赞弹簧动画时长（ms） */
  POP_MS: 240,
} as const;

export const MAP = {
  /** 磁贴边长（rpx） */
  TILE_RPX: 240,
  /** 网格步长（rpx） */
  CELL_RPX: 300,
  /** 最小缩放倍数 */
  MIN_SCALE: 0.55,
  /** 最大缩放倍数 */
  MAX_SCALE: 3,
  /** 点击判定最大位移（px） */
  TAP_MOVE_PX: 8,
  /** 点击判定最大时长（ms） */
  TAP_DURATION_MS: 320,
  /** 越界橡皮筋系数（越小越硬）：松手位附近每 1px 越界的跟手比例 */
  RUBBER: 0.35,
  /** 单轴越界位移的饱和基准（px）：越界到这个量级时画布几乎不再跟手 */
  RUBBER_TRAVEL: 120,
  /** 惯性衰减系数 */
  FRICTION: 0.92,
  /** 最小惯性速度 */
  MIN_SPEED: 0.12,
  /** 松手后弹性收敛到合法边界的时长（ms） */
  SETTLE_MS: 220,
  /** 越界 / 手势结束时的收敛时长（ms，略快于常规收敛） */
  EDGE_SETTLE_MS: 200,
} as const;

export const REQUEST = {
  /** 请求超时（ms） */
  TIMEOUT_MS: 15000,
  /** 窗口 resize 防抖（ms） */
  RESIZE_DEBOUNCE_MS: 120,
} as const;

export const STORAGE = {
  /** 学生端 token 存储键 */
  STUDENT_TOKEN: 'student_token',
  /** 主题模式存储键 */
  THEME: 'theme',
  /** 纠错提交去重记录存储键（同一设备内软提示的依据） */
  ISSUE_SUBMITTED: 'dish-issue-submitted',
} as const;
