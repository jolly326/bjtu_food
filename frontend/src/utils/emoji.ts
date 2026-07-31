/**
 * EMOJI 图标映射常量（小程序 MVP 占位策略）
 * ------------------------------------------------------------
 * 依据 project_spec.md §0.6 一致性红线第 3 条 + §4.2 / §4.9②：
 *  - MVP 阶段统一使用 emoji 作为图标占位，禁止 iconfont SVG。
 *  - 语义唯一约束：『喜欢/收藏』只保留一个爱心语义（❤️）；
 *    『有用/点赞』用 👍 区分，二者不可混用。
 *  - 所有 emoji 字面量集中在此文件登记，组件内禁止散落硬编码。
 *
 * 命名采用 camelCase，key 即语义，便于全局检索与复用。
 */

/** 喜欢 / 收藏（唯一爱心语义，禁止承载「点赞/有用」） */
export const LIKE = '❤️'

/** 有用 / 点赞（与 ❤️ 不可混用） */
export const USEFUL = '👍'

/** 热门 */
export const HOT = '🔥'

/** 火热（热搜/黑马强度） */
export const FIRE = '🔥'

/** 限时 */
export const LIMITED = '⏰'

/** 猜你喜欢 */
export const GUESS = '💡'

/** 搜索 */
export const SEARCH = '🔍'

/** 位置 */
export const LOCATION = '📍'

/** 评价 */
export const REVIEW = '💬'

/** 分享 */
export const SHARE = '📤'

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

/** 加号 / 发布入口（task-06 悬浮 ➕） */
export const PLUS = '➕'

/** 消息 / 通知中心（task-09） */
export const BELL = '🔔'

/** 设置 / 齿轮 */
export const SETTINGS = '⚙️'

/** 清单 / 收藏夹 */
export const LIST = '📑'

/** 删除 / 垃圾桶 */
export const DELETE = '🗑️'

/** 举报 / 警示（社区举报入口，task-12.7） */
export const REPORT = '⚠️'

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

/** 集中导出：组件 / 页面统一使用 EMOJI.xxx 访问 */
export const EMOJI = {
  like: LIKE,
  useful: USEFUL,
  hot: HOT,
  fire: FIRE,
  limited: LIMITED,
  guess: GUESS,
  search: SEARCH,
  location: LOCATION,
  review: REVIEW,
  share: SHARE,
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
  plus: PLUS,
  bell: BELL,
  settings: SETTINGS,
  list: LIST,
  history: HISTORY,
  delete: DELETE,
  report: REPORT,
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
