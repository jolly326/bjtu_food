/**
 * EMOJI 常量（仅保留非图标语义 / 装饰性占位）
 * ------------------------------------------------------------
 * 注意：功能 / 情感图标（搜索 / 位置 / 喜欢 / 有用 / 热门 / 限时 /
 * 猜你喜欢 / 分享 / 评价 / 发布 / 举报 等）已全部迁移为 SVG 矢量图标，
 * 统一经 `components/IconSvg.vue` 引用（见 task-15 / project_spec §4.2 / §4.9）。
 * 本文件仅保留少量非图标语义的 emoji 占位（如空状态、菜品占位、星级、
 * 标签属性等），新增图标语义须登记到 IconSvg 而非此处。
 */

/** 空状态 */
export const EMPTY = '🍽'

/** 星星（评分实心） */
export const STAR_FILLED = '⭐'

/** 星星（评分空心） */
export const STAR_EMPTY = '☆'

/** 菜品占位图 */
export const DISH_PLACEHOLDER = '🍜'

/** 首页（TabBar） */
export const HOME = '🏠'

/** 我的（TabBar） */
export const PROFILE = '👤'

/** 新活动徽标 */
export const NEW = '🆕'

/** 促销 / 活动徽标 */
export const PROMOTION = '🎉'

/** 返回箭头 */
export const BACK = '↩️'

/** 右箭头（菜单 / 选择器） */
export const ARROW_RIGHT = '➡️'

/** 日历 / 活动周期 */
export const CALENDAR = '📅'

/** 编辑 / 我的发布 */
export const EDIT = '✏️'

/** 反馈 / 联系开发者 */
export const CONTACT = '📨'

/** 链接 / 跳转外部（查看公众号原文等） */
export const LINK = '🔗'

/** 邮箱 */
export const EMAIL = '📧'

/** 提交成功 */
export const SUCCESS = '✅'

/** 消息 / 通知中心（task-09） */
export const BELL = '🔔'

/** 设置 / 齿轮 */
export const SETTINGS = '⚙️'

/** 清单 / 收藏夹 */
export const LIST = '📑'

/** 删除 / 垃圾桶 */
export const DELETE = '🗑️'

/** 足迹 / 历史 */
export const HISTORY = '🕘'

/** 锁 / 密码 */
export const LOCK = '🔒'

/** 开锁（营业中） */
export const LOCK_OPEN = '🔓'

/** 辣度（🌶️ 级数，task-03 属性标签） */
export const CHILI = '🌶️'

/** 分量（task-03 属性标签） */
export const PORTION = '🍱'

/** 时钟 / 营业时间 / 供应时段（task-03） */
export const CLOCK = '🕐'

/** 图片 / 晒图（task-03 评价区） */
export const IMAGE = '🖼️'

/** 价格 / 消费水平（find 页人均） */
export const PRICE = '💰'

/** 筛选 / 过滤条件（find 页筛选入口） */
export const FILTER = '⚙️'

/** 食堂菜品分区标题 */
export const CANTEEN_DISH = '🍽'

/** 集中导出：组件 / 页面统一使用 EMOJI.xxx 访问（仅非图标语义占位） */
export const EMOJI = {
  empty: EMPTY,
  starFilled: STAR_FILLED,
  starEmpty: STAR_EMPTY,
  dishPlaceholder: DISH_PLACEHOLDER,
  home: HOME,
  profile: PROFILE,
  new: NEW,
  promotion: PROMOTION,
  back: BACK,
  arrowRight: ARROW_RIGHT,
  calendar: CALENDAR,
  edit: EDIT,
  contact: CONTACT,
  link: LINK,
  email: EMAIL,
  success: SUCCESS,
  lock: LOCK,
  bell: BELL,
  settings: SETTINGS,
  list: LIST,
  history: HISTORY,
  delete: DELETE,
  lockOpen: LOCK_OPEN,
  chili: CHILI,
  portion: PORTION,
  clock: CLOCK,
  image: IMAGE,
  canteenDish: CANTEEN_DISH,
  price: PRICE,
  filter: FILTER,
} as const

export default EMOJI
