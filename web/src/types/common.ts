/**
 * 管理后台通用类型与全部 VO / 请求契约。
 *
 * <p>权威源：`docs/api/web/**`（管理端契约真源：端点 / 字段 / 错误码）+ `docs/schema/**`（库表）。
 * 后端 Java DTO/VO 出参统一 camelCase，视图层禁止手写字段映射。
 */

// ===== 统一响应信封 =====
/**
 * 管理端分页壳（`AdminPageResult<T>`，见 [api/README](../../../docs/api/README.md)）。
 *
 * <p>**仅 `/admin/**` 使用** —— 管理端是核对型使用，需要「共 N 条 + 页码」；
 * 学生端 `PageResult` 仍只回 `records`（本仓库前端不消费学生端契约）。
 */
export interface AdminPage<T> {
  records: T[]
  /** 满足条件的总条数（总页数 = ceil(total / pageSize)，pageSize 固定 20） */
  total: number
}

// ===== 业务状态码 =====
export const CODE_OK = 200
/**
 * **401 = 会话失效**（未带 token / 签名无效 / 已过期 / 账号已停用，或登录失败）——
 * 由**请求层统一**处理：清 token → 跳登录页，页面不渲染列表态（[UI 基线 §1.5 ⑥](../../../docs/ui/web/公共组件与形态基线.md)）。
 */
export const CODE_UNAUTHORIZED = 401

// ===== 通用状态枚举（文案以 设计变量.md 的「状态文案总表」为准） =====
export type OnOffStatus = 'on' | 'off'
export type UserStatus = 'active' | 'disabled' | 'deleted'
export type FeedbackStatus = 'pending' | 'handled'
export type CorrectionStatus = 'pending' | 'adopted' | 'rejected'

/**
 * 菜品问题反馈类型（`dish_correction.type`）
 *
 * - `field`：**信息有误** —— 局部提交改动项，处置=差异对照 + 逐项采纳 / 拒绝
 * - `gone`：**已经下架** —— 一键提交即成立，处置=🔴 **仅「下架」**（绝不删除）
 */
export type CorrectionType = 'field' | 'gone'

// ===== D1 运营看板 =====

/** 最近待办项（`kind` 决定跳哪个处置页） */
export interface RecentTodoVO {
  kind: 'feedback' | 'report' | 'correction'
  id: number
  /** 摘要：反馈 = 正文摘要；举报 = 被举报评价摘要；纠错 = 菜品名 */
  title: string
  submittedAt: string | null
}

export interface DashboardVO {
  /** ① 待办（三计数 + **最近 5 条可点击**） */
  todo: {
    pendingFeedbackCount: number
    pendingReportCount: number
    pendingCorrectionCount: number
    recent: RecentTodoVO[]
  }
  /** ② 主数据健康度（只收管理员当场能修的项） */
  health: {
    dishesWithoutImage: number
    dishesWithoutStall: number
    dishesWithoutCategory: number
    stallsWithoutDish: number
  }
  /** ③ 概况（只读规模） */
  overview: {
    userCount: number
    verifiedUserCount: number
    onSaleDishCount: number
    reviewCount: number
  }
}

// ===== A1 食堂 =====
export interface CanteenAdminVO {
  id: number
  name: string
  /** 其下档口数（删除受阻判据 + 列表展示，联表统计） */
  stallCount: number
  /** 地点（恒非空串，无为空串） */
  location: string
  /** 描述（恒非空串） */
  description: string
  /** 图片绝对 URL 数组（有序，首图作封面；无图 `[]`） */
  images: string[]
  /** 排序位（食堂列表的排序键：升序；无拖动入口，在详情表单内维护） */
  sortOrder: number
  updatedAt: string
}

export interface CanteenSaveReq {
  /** 名称（1~64 字；应用层查重，重名 400）—— 必填，整体替换 */
  name: string
  /** 地点（≤128 字；缺省 = 保持原值，空串 = 清空） */
  location?: string
  /** 描述（≤512 字；缺省 = 保持原值，空串 = 清空） */
  description?: string
  /** 图片（有序，首图作封面；缺省 = 保持原值，`[]` = 清空） */
  images?: string[]
  /** 排序位（缺省 = 保持原值） */
  sortOrder?: number
}

// ===== A2 档口 =====
export interface StallAdminVO {
  id: number
  canteenId: number
  /** 所属食堂名（联表带出） */
  canteenName: string
  name: string
  /** 楼层（受控字典、**值即汉字**：负一层 / 一层 / 二层 / 三层 / 四层） */
  floor: string
  windowNo: string
  /** 地点（恒非空串，无为空串） */
  location: string
  /** 描述（恒非空串） */
  description: string
  /** 图片绝对 URL 数组（有序，首图作封面；无图 `[]`） */
  images: string[]
  /** 平均评分（实时聚合，2 位小数；无评价按 0.00） */
  avgRating: number
  /** 排序位（纠错候选档口列表的排序键；本列表不按其排序，在详情表单内维护） */
  sortOrder: number
  /** 其下菜品数（删除受阻判据） */
  dishCount: number
  updatedAt: string
}

export interface StallSaveReq {
  canteenId: number
  name: string
  floor?: string
  windowNo?: string
  /** 地点（≤128 字；缺省 = 保持原值，空串 = 清空） */
  location?: string
  /** 描述（≤512 字；缺省 = 保持原值，空串 = 清空） */
  description?: string
  /** 图片（有序，首图作封面；缺省 = 保持原值，`[]` = 清空） */
  images?: string[]
  /** 排序位（缺省 = 保持原值） */
  sortOrder?: number
}

// ===== A3 菜品 =====
/** 描述属性：键 = 维度 ID（字符串形态），值 = 中文文本 / 数组 */
export type DishAttributes = Record<string, string | string[]>

/** A3 菜品列表行（**瘦身**：不含 description / attributes / 全量 images；封面 = 首图派生值） */
export interface DishAdminListItemVO {
  id: number
  stallId: number
  stallName: string
  canteenName: string
  name: string
  price: number
  originalPrice: number | null
  /** 封面图绝对 URL（首图派生；无图为空串） */
  coverImage: string
  mealTypeId: number
  /** 分类中文名（A6 分类值字典派生） */
  mealTypeLabel?: string
  status: OnOffStatus
  avgRating: number | null
  /** 评价数（删除确认的影响面来源） */
  ratingCount: number
  /**
   * 近 30 天浏览量（`dish_view_log` 滚动窗口统计）。
   *
   * 🔴 **不是**历史累计的 `dish.view_count`（该列已停写）：一个是 30 天窗口值（会随时间回落），
   * 一个是单调累计值。运营用途 = 看出「哪道菜多人看但没评价」⇒ 判断是否引导其产出评价。
   */
  recentViewCount?: number
  updatedAt: string
}

export interface DishAdminVO {
  id: number
  name: string
  price: number
  originalPrice: number | null
  description: string
  /** 图片地址（本地链路站内相对路径 / COS 链路绝对 URL；出参已由服务端转绝对） */
  images: string[]
  stallId: number
  stallName: string
  canteenName: string
  /** 分类值 ID（= `dish.meal_type`，值域来自 A6 分类值字典） */
  mealTypeId: number
  /** 分类中文名（A6 分类值字典派生，列表直接展示，端上零硬编码） */
  mealTypeLabel?: string
  attributes: DishAttributes | null
  status: OnOffStatus
  avgRating: number | null
  ratingCount: number
  createdAt: string
  updatedAt: string
}

export interface DishSaveReq {
  name: string
  /** 归属只认 `stallId`（实体下拉），不再支持按名 upsert */
  stallId: number
  price: number
  originalPrice?: number | null
  /** 分类值 ID（须存在于 A6 分类值字典） */
  mealTypeId: number
  description?: string
  /** 0~5 张、有序、首图作封面 */
  images: string[]
  attributes?: DishAttributes
}

export interface DishListParams {
  page?: number
  pageSize?: number
  keyword?: string
  canteenId?: number
  stallId?: number
  mealTypeId?: number
  status?: OnOffStatus | ''
}

// ===== A4 属性维度与取值 =====
export interface DishDimensionAdminVO {
  /** 维度 ID（= `dish.attributes` JSON 的键） */
  id: number
  name: string
  valueType: 'single' | 'multi'
  /** 是否**系统维度**（true = 菜品种类：取值类型不可改、维度不可删、取值不写入 attributes） */
  system: boolean
  order: number
  dishCount: number
  valueCount: number
  updatedAt: string
}

export interface DishValueAdminVO {
  id: number
  dimensionId: number
  label: string
  order: number
  dishCount: number
  updatedAt: string
}

export interface DishDimensionSaveReq {
  name: string
  valueType: 'single' | 'multi'
}

export interface DishValueSaveReq {
  label: string
}

/** 拖拽排序的整体提交体（A4 / A5 / A6 / A7 四个 `PUT .../sort` 共用） */
export interface SortItemsReq {
  items: { id: number; order: number }[]
}

// ===== A4 属性维度与取值（续） =====

// ===== A5 首页 Banner =====
export interface BannerAdminVO {
  id: number
  imageUrl: string
  order: number
  status: OnOffStatus
  createdAt: string
  updatedAt: string
}

export interface BannerSaveReq {
  imageUrl: string
}

// ===== A6 首页筛选视图与分类（菜品种类） =====

/** 排序口径白名单（唯一真源 = 后端条件引擎） */
export type DishSortKind =
  | 'random'
  | 'priceAsc'
  | 'priceDesc'
  | 'discountDesc'
  | 'ratingDesc'
  | 'newest'

/** 筛选条件字段白名单（唯一真源 = 后端条件引擎；操作符随字段而定） */
export type DishViewField =
  | 'mealTypeId'
  | 'discount'
  | 'price'
  | 'stallId'
  | 'canteenId'
  | 'avgRating'
  | 'createdAt'

/** 视图筛选条件的一项（`{ field, op, value? , values? }`）—— AND 组合的有限语言 */
export interface DishViewCondition {
  field: DishViewField | ''
  op: string
  /** 单值（`=` / `>=` / `<=` / `withinDays` 用；`between` 时为下界） */
  value?: string
  /** 多值（`in` 用；`between` 时为 [下界, 上界]） */
  values?: string[]
}

export interface DishViewAdminVO {
  id: number
  /** tab 文案 */
  label: string
  order: number
  enabled: boolean
  /** 筛选条件（AND；`[]` = 不筛选） */
  conditions: DishViewCondition[]
  /** 排序口径 */
  sortKind: DishSortKind
  /** 当前匹配的在售菜品数（列表展示，兼作「是否生效」的自证） */
  matchedCount: number
  updatedAt: string
}

/** 视图新建入参（不含 `order` / `enabled`：顺序由拖拽维护、新建即启用） */
export interface DishViewCreateReq {
  label: string
  conditions: DishViewCondition[]
  sortKind: DishSortKind
}

/** 视图修改入参：文案 / 启停 / 条件 / 排序口径**整体替换**（缺任一 → 400） */
export interface DishViewUpdateReq {
  label: string
  enabled: boolean
  conditions: DishViewCondition[]
  sortKind: DishSortKind
}

/** A6 菜品种类（系统维度取值；`dish.meal_type_id` 存它的 `id`，无机器键） */
export interface DishCategoryAdminVO {
  id: number
  label: string
  order: number
  /** 引用该种类的菜品数（删除前判断） */
  dishCount: number
  updatedAt: string
}

// ===== A7 举报原因 =====
export interface ReportReasonAdminVO {
  /** 原因 ID（落库列 = `user_feedback.sub_reason_id`） */
  id: number
  label: string
  order: number
  status: OnOffStatus
  /** 被举报记录引用次数（`type='report'` 且 `sub_reason_id = id`）—— 删除前判断 */
  feedbackCount: number
  updatedAt: string
}

// ===== B1 评价 =====
export interface ReviewAdminVO {
  id: number
  dishId: number
  dishName: string | null
  userId: number
  userNickname: string
  /** 评价者头像绝对 URL（联表带出；游客 / 未设置 / 已注销 → null，走统一占位） */
  userAvatar: string | null
  rating: number
  content: string
  images: string[]
  /** 是否被隐藏（B1 口径：**boolean**，库列 `is_hidden` 仍是 0/1，服务端翻译） */
  hidden: boolean
  /** 隐藏附注（≤200 字，随隐藏回执下发；未隐藏为 null） */
  hiddenNote: string | null
  createdAt: string
}

export interface ReviewListParams {
  page?: number
  pageSize?: number
  keyword?: string
  dishId?: number
  userId?: number
  /** `true` = 仅已隐藏；`false` = 仅显示中；不传 = 全部 */
  hidden?: boolean | ''
}

export interface ReviewHiddenReq {
  hidden: boolean
  /** 隐藏附注（可选，≤200 字；仅 `hidden = true` 时有意义） */
  note?: string
}

// ===== B2 / B3 反馈与举报（同一端点，`category` 分流） =====
export interface FeedbackAdminVO {
  id: number
  /** 反馈类型（白名单：`suggestion` / `add` / `error`；存量可能有 `bug` / `other`） */
  type: string
  /** 提交人 ID（**`0` = 匿名提交**；静默登录的游客为真实 ID） */
  userId: number
  userNickname: string
  content: string
  images: string[]
  status: FeedbackStatus
  /** 处理结论（表无物理列，按 `status` + `rejectReason` 派生；`pending` 为 null） */
  outcome: 'handled' | 'rejected' | null
  reply: string | null
  rejectReason: string | null
  createdAt: string
  handledAt: string | null
}

/** B3 举报行 = 反馈行 + 被举报评价摘要与可见性 */
export interface ReportAdminVO extends FeedbackAdminVO {
  /** 举报原因 ID（= `user_feedback.sub_reason_id` = `report_reason.id`） */
  subReasonId: number
  /** 举报原因中文名（服务端按 ID 实时翻译） */
  reasonLabel: string
  reviewId: number
  reviewContent: string | null
  reviewDishName: string | null
  /** 被举报评价当前是否已隐藏（决定「同时隐藏」复选是否置灰） */
  reviewHidden: boolean
}

export interface FeedbackListParams {
  page?: number
  pageSize?: number
  /** `feedback` = 意见反馈（B2）｜`report` = 举报（B3）｜不传 = 全部 */
  category?: 'feedback' | 'report' | ''
  status?: FeedbackStatus | ''
  type?: string
  keyword?: string
  userId?: number
}

export interface FeedbackHandleReq {
  /** 处理回复（≤600 字；`handled` 时可选、`rejected` 时作为处理说明随回执下发） */
  reply?: string
  outcome?: 'handled' | 'rejected'
  /** 不采纳原因（`rejected` 必填，≤200 字） */
  rejectReason?: string
  /** B3 专用：处置举报时**同时隐藏**被举报评价（默认勾选；评价已隐藏时置灰） */
  hideReview?: boolean
}

// ===== B4 菜品问题反馈 =====
export interface CorrectionAdminVO {
  id: number
  /** 问题类型：field=信息有误 / gone=已经下架（管理端据此分 Tab 与分派处置） */
  type: CorrectionType
  dishId: number
  dishName: string | null
  /** 提交人 ID（**`0` = 匿名提交**；昵称为空时页面回落「游客」） */
  userId: number
  /** 提交人昵称（匿名提交为空串 / null） */
  userNickname: string
  /** **仅 `type=gone`**：补充说明（≤200 字，处置时**必须展示**给管理员判读） */
  note: string | null
  status: CorrectionStatus
  reply: string | null
  rejectReason: string | null
  /** 差异项数量（`field` 型「改了几项」；`gone` 型恒 0） */
  changeCount: number
  /** `field` 型含 `floor` 改动时的连带影响提示（同档口菜品数，文案由服务端下发）；否则为 null */
  floorImpact: string | null
  createdAt: string
  handledAt: string | null
}

/** 菜品问题反馈详情 */
export interface CorrectionDetailVO {
  id: number
  /**
   * 问题类型：`field` → 展示 `differences` 逐项采纳；`gone` → `differences` 恒空，
   * 处置动作**仅「下架」**（🔴 本流程不提供删除）。
   */
  type: CorrectionType
  dishId: number
  dishName: string | null
  /** 提交人 ID（**`0` = 匿名提交**；昵称为空时页面回落「游客」） */
  userId: number
  /** 提交人昵称（匿名提交为空串 / null） */
  userNickname: string
  status: CorrectionStatus
  /** `field` 型：仍有差异的项（逐项勾选采纳）；`gone` 型：恒空 */
  differences: CorrectionDifference[]
  /** `gone` 型：用户选填补充说明（≤200 字） */
  note: string | null
  /** `gone` 型：同菜品待处理反馈数（已按用户去重）—— ⚠️ **仅参考，非下架阈值**（≥1 即进队列，是否下架人工决定） */
  goneUserCount: number | null
  images: string[]
  reply: string | null
  rejectReason: string | null
  createdAt: string
  handledAt: string | null
}

export interface CorrectionDifference {
  /** 改动字段：`name` / `price` / `canteenName` / `stallName` / `floor` / `attributes` / `images` */
  field: string
  /** 字段中文名（服务端下发，端上零硬编码） */
  label: string
  oldValue: string
  newValue: string
  /** 楼层改动会连带同档口其它菜品（UI 需高亮提示） */
  affectsOthers: boolean
}

export interface CorrectionListParams {
  page?: number
  pageSize?: number
  status?: CorrectionStatus | ''
  /** 问题类型筛选：`field` / `gone` / `''`（= 全部，管理端分 Tab 用） */
  type?: CorrectionType | ''
  dishId?: number
}

/** 采纳请求：`field` 型带 `acceptedFields`；`gone` 型无差异项（只认 `reply`） */
export interface CorrectionAdoptReq {
  /** 采纳哪些差异项（`field` 型**必填且非空**；取值 = `differences[].field`） */
  acceptedFields?: string[]
  stallId?: number
  createIfMissing?: boolean
  /** 采纳附注（可选，≤600 字；随回执下发，留空用服务端固定文案） */
  reply?: string
}

export interface StallConfirmVO {
  needStallConfirm: true
  candidates: { id: number; name: string }[]
}

export interface CorrectionRejectReq {
  /** 处理说明（≤1000 字）—— **可选**（B4 起 reply 可选；留空时回执以不采纳原因为正文） */
  reply?: string
  /** 不采纳原因（必填，≤200 字） */
  rejectReason: string
}

// ===== C2 用户 =====
export interface UserAdminVO {
  id: number
  /** 账号标识（微信建号为 `wx_<openid 尾 16 位>`；注销后为 `deleted_{id}`） */
  username: string
  nickname: string
  /** 头像绝对 URL（无为空串） */
  avatar: string
  status: UserStatus
  /** 是否已绑定微信（**只给布尔，不暴露 `openid` 明文**） */
  wechatBound: boolean
  /** 已认证绑定的校园邮箱（空串 = 未认证；**认证态唯一判据**） */
  bindEmail: string
  /** 注册时间（`yyyy-MM-dd HH:mm:ss`） */
  createdAt: string
  /** 最近更新时间（`yyyy-MM-dd HH:mm:ss`；零值显示 `—`） */
  updatedAt: string
}

export interface UserListParams {
  page?: number
  pageSize?: number
  keyword?: string
  status?: UserStatus | ''
}

export interface UserStatusReq {
  /** `disabled` = 禁用；`active` = 启用（恢复 `active`）；启停类端点统一用 `status` 字符串枚举 */
  status: 'active' | 'disabled'
}

// ===== 管理端安全告警 =====
/** 告警级别键（与后端 `AlertType.Severity` 对齐） */
export type AlertSeverity = 'info' | 'warn' | 'critical'

export interface SecurityAlertVO {
  id: number
  /** 告警类型键（后端枚举名，如 `LOGIN_LOCKOUT`） */
  alertType: string
  /** 类型中文标签（**写入时快照**，不随枚举重命名变化） */
  alertTypeLabel: string
  /** 级别键：`info` 提示 / `warn` 警告 / `critical` 严重 */
  severity: AlertSeverity | string
  /** 级别中文标签（写入时快照） */
  severityLabel: string
  title: string
  /** 明细（脱敏；无明细为空串） */
  detail: string
  /** 来源 IP（无 Web 上下文时为空串） */
  sourceIp: string
  /** 发生时间（`yyyy-MM-dd HH:mm:ss`） */
  createdAt: string
}

export interface AlertListParams {
  page?: number
  pageSize?: number
  /** 告警类型键；空串 / 不传 = 全部 */
  alertType?: string
  /** 级别键；空串 / 不传 = 全部 */
  severity?: AlertSeverity | ''
}
