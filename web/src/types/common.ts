/**
 * 管理后台通用类型与全部 VO / 请求契约。
 *
 * <p>权威源：`docs/web/**`（管理端功能文档，接口/字段/错误码真源）+ `docs/api/README.md`（通用约定）。
 * 后端 Java DTO/VO 出参统一 camelCase，视图层禁止手写字段映射。
 */

// ===== 统一响应信封 =====
export interface Result<T> {
  code: number
  message: string
  data: T | null
}

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
export const CODE_BAD_REQUEST = 400
/** 会话失效（口令不匹配 / 账号受限）—— 页面渲染第 ⑥ 态且不渲染重试 */
export const CODE_FORBIDDEN = 403
export const CODE_NOT_FOUND = 4001
export const CODE_SERVER_ERROR = 500

// ===== 通用状态枚举（文案以 设计变量.md 的「状态文案总表」为准） =====
export type OnOffStatus = 'on' | 'off'
export type UserStatus = 'active' | 'disabled' | 'deleted'
export type FeedbackStatus = 'pending' | 'handled'
export type CorrectionStatus = 'pending' | 'adopted' | 'rejected'

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
  updatedAt: string
}

export interface CanteenSaveReq {
  /** 名称（1~64 字；应用层查重，重名 400） */
  name: string
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
  /** 其下菜品数（删除受阻判据） */
  dishCount: number
  updatedAt: string
}

export interface StallSaveReq {
  canteenId: number
  name: string
  floor?: string
  windowNo?: string
}

// ===== A3 菜品 =====
/** 描述属性：键 = 维度 `fieldKey`，值 = 中文文本 / 数组 */
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
  mealType: string
  /** 分类中文名（A6 分类值字典派生） */
  mealTypeLabel?: string
  status: OnOffStatus
  avgRating: number | null
  /** 评价数（删除确认的影响面来源） */
  ratingCount: number
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
  /** 分类键（= `dish.meal_type`，值域来自 A6 分类值字典） */
  mealType: string
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
  /** 分类键；**允许新键并自动登记**（见 A6 值管理） */
  mealType: string
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
  mealType?: string
  status?: OnOffStatus | ''
}

// ===== A4 属性维度与取值 =====
export interface DishDimensionAdminVO {
  id: number
  fieldKey: string
  name: string
  valueType: 'single' | 'multi'
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
  fieldKey: string
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

// ===== A6 首页筛选视图与分类值 =====
export interface DishViewAdminVO {
  id: number
  /** 视图键（端上回传 `view=<key>`；在用后不可改） */
  key: string
  /** tab 文案 */
  label: string
  order: number
  enabled: boolean
  /** 默认视图（端上不带 `view` 的落点；全站恰一个） */
  isDefault: boolean
  sortKind: string
  /** 筛选条件（原样回显，供编辑；空数组 = 全部菜品） */
  conditions: DishViewCondition[]
  /** 当前匹配的在售菜品数 */
  matchedCount: number
  updatedAt: string
}

/** 视图筛选条件项（**有限语言**的一元；字段/操作符/值三层白名单由服务端强制） */
export interface DishViewCondition {
  /** 白名单：`mealType` / `discount` / `price` / `stallId` / `canteenId` / `avgRating` / `createdAt` */
  field: string
  /** 白名单（随字段而定）：`=` / `in` / `isTrue` / `between` / `>=` / `<=` / `withinDays` */
  op: string
  /** 单值（`= / >= / <= / withinDays` 用；`between` 时为下界）；`discount.isTrue` 无值 */
  value?: string
  /** 多值（`in` 用；`between` 时为 `[下界, 上界]`） */
  values?: string[]
}

export interface DishViewSaveReq {
  key: string
  label: string
  sortKind: string
  enabled: boolean
  conditions: DishViewCondition[]
}

/** 预览出参（保存前试算，不入库） */
export interface DishViewPreviewVO {
  matchedCount: number
  sampleNames: string[]
}

/** A6 分类值（`dish.meal_type` 的取值域；条件构建器的「值」就是它的 `key`） */
export interface DishCategoryAdminVO {
  id: number
  key: string
  label: string
  order: number
  /** 引用该分类的菜品数（删除前判断） */
  dishCount: number
  updatedAt: string
}

// ===== A7 举报原因 =====
export interface ReportReasonAdminVO {
  id: number
  /** 机器值（端上提交字段名 = `reason`；落库列 = `user_feedback.sub`；**在用后不可改**） */
  value: string
  label: string
  order: number
  status: OnOffStatus
  /** 被举报记录引用次数（`type='report'` 且 `sub = value`）—— 删除前判断 */
  feedbackCount: number
  updatedAt: string
}

export interface ReportReasonSaveReq {
  value: string
  label: string
}

// ===== B1 评价 =====
export interface ReviewAdminVO {
  id: number
  dishId: number
  dishName: string | null
  userId: number
  userNickname: string
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
  /** 举报原因机器值（= `user_feedback.sub`） */
  reason: string
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

// ===== B4 菜品纠错 =====
export interface CorrectionAdminVO {
  id: number
  dishId: number
  dishName: string | null
  userId: number
  userNickname: string
  status: CorrectionStatus
  reply: string | null
  rejectReason: string | null
  createdAt: string
  handledAt: string | null
}

/** 纠错详情：列表行 + 逐项差异对照（供「逐项勾选采纳」） */
export interface CorrectionDetailVO {
  id: number
  dishId: number
  dishName: string | null
  userId: number
  userNickname: string
  status: CorrectionStatus
  differences: CorrectionDifference[]
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
  dishId?: number
}

/** 采纳请求：`stallId`（挂靠既有档口）或 `createIfMissing: true`（按提交名新建） */
export interface CorrectionAdoptReq {
  acceptedFields?: string[]
  stallId?: number
  createIfMissing?: boolean
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
  nickname: string
  avatar: string
  /** 空串 = 未认证（管理端 VO 恒非空串；**认证态唯一判据**） */
  bindEmail: string
  status: UserStatus
  reviewCount: number
  createdAt: string
}

export interface UserListParams {
  page?: number
  pageSize?: number
  keyword?: string
  status?: UserStatus | ''
}

export interface UserStatusReq {
  /** `true` = 禁用；`false` = 启用（恢复 `active`） */
  disabled: boolean
}
