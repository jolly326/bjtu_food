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
/**
 * 菜品问题反馈处置状态（以处置任务表 `moderation_task` 为判据，不由工单表自身列表达）。
 * **已处置的两种结论由 `outcome` 区分**（已受理 / 不采纳）。
 */
export type CorrectionStatus = 'pending' | 'handled'

/**
 * 菜品问题反馈类型（`dish_correction.type`）
 *
 * - `field`：**信息有误** —— 局部提交改动项，详情给「当前值 / 提交值 + 是否一致」的只读对照
 * - `gone`：**已经下架** —— 一键提交即成立，`differences` 恒空（读 `note` + `images` + `goneUserCount`）
 *
 * 两类**处置动作相同**（标记已处理 / 驳回），均只归档结论 + 投递回执、**不改写菜品**；
 * 菜品资料修改与可逆下架由管理员在「菜品管理」手动执行。
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
    /** 分类为空的菜品数（`dish.category_id IS NULL`） */
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
  /** 平均评分（实时聚合，**1 位小数**；无可见评价按 `0.0`，不为 `null`） */
  avgRating: number
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
}

// ===== A3 菜品 =====
/** 描述属性：键 = 维度 ID（字符串形态），值 = 中文文本 / 数组 */
export type DishAttributes = Record<string, string | string[]>

/** A3 菜品列表行（**瘦身**：不含 description / attributes / 全量 images；封面 = 首图派生值） */
export interface DishAdminListItemVO {
  id: number
  /**
   * 所属档口 ID；**`null` = 未归入档口**。
   *
   * <p>管理端列表用 **LEFT JOIN** 取归属，故未归入档口的存量行**在 A3 可见**（配合 `withoutStall=true`
   * 直接定位修复）；学生端路径仍是 INNER JOIN，这类菜品不出现（见 [docs/schema/dish.md](../../../docs/schema/dish.md)）。
   */
  stallId: number | null
  stallName: string
  canteenName: string
  name: string
  price: number
  originalPrice: number | null
  /** 封面图绝对 URL（首图派生；无图为空串） */
  coverImage: string
  /** 菜品种类 ID（= `dish.category_id`，值域来自 A6 种类字典 `dish_category`） */
  categoryId: number
  /** 种类中文名（服务端按 `dish_category.id` 查名带出） */
  categoryName?: string
  status: OnOffStatus
  avgRating: number | null
  /** 评价数（删除确认的影响面来源） */
  ratingCount: number
  /**
   * 近 30 天浏览量（`dish_view_log` 明细的滚动窗口统计）。
   *
   * 🔴 浏览量的**唯一载体**是 `dish_view_log`（一行 = 一次浏览），本值即它的 30 天窗口聚合
   * ⇒ **会随时间回落**，不是单调累计值。运营用途 = 看出「哪道菜多人看但没评价」
   * ⇒ 判断是否引导其产出评价。
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
  /**
   * 所属档口 ID；**`null` = 未归入档口**。
   *
   * <p>该形态只可能出现在**按 id 直查的单条详情**：列表走 `INNER JOIN stall / canteen`，
   * 未归入档口的菜品不出现在任何列表中（[docs/schema/dish.md](../../../docs/schema/dish.md)）。
   */
  stallId: number | null
  stallName: string
  canteenName: string
  /** 菜品种类 ID（= `dish.category_id`，值域来自 A6 种类字典 `dish_category`） */
  categoryId: number
  /** 种类中文名（服务端按 `dish_category.id` 查名带出，列表直接展示，端上零硬编码） */
  categoryName?: string
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
  /** 菜品种类 ID（须存在于 A6 种类字典 `dish_category`） */
  categoryId: number
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
  /** 菜品种类筛选（值 = `dish_category.id`） */
  categoryId?: number
  status?: OnOffStatus | ''
  /**
   * 只看**未归入档口**（`stall_id` 为空）的菜品 —— 看板健康度项的定位入口。
   *
   * <p>与 `stallId` **互斥**（同时传服务端 `400`）：两者同时成立时结果恒空，
   * 静默返回空集会被误读为「筛完就是没有」。
   */
  withoutStall?: boolean
}

// ===== A4 属性维度与取值 =====
export interface DishDimensionAdminVO {
  /** 维度 ID（= `dish.attributes` JSON 的键） */
  id: number
  name: string
  /** 取值类型（`single` 单值 / `multi` 多值；全部维度均可改） */
  valueType: 'single' | 'multi'
  dishCount: number
  valueCount: number
  updatedAt: string
}

export interface DishValueAdminVO {
  id: number
  dimensionId: number
  name: string
  dishCount: number
  updatedAt: string
}

export interface DishDimensionSaveReq {
  name: string
  valueType: 'single' | 'multi'
}

export interface DishValueSaveReq {
  name: string
}

// ===== A5 首页 Banner（全站唯一保留排序位的资源） =====
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

/** 拖拽排序的整体提交体（`[{ id, order }]`，全量提交） */
export interface SortItemsReq {
  items: { id: number; order: number }[]
}

// ===== A6 首页筛选视图与分类（菜品种类） =====

export interface DishViewAdminVO {
  id: number
  /** tab 文案 */
  name: string
  /** 筛选种类 ID（`null` = 「为你推荐」，不按种类筛） */
  categoryId: number | null
  enabled: boolean
  /** 当前匹配的在售菜品数（列表展示，兼作「是否生效」的自证） */
  matchedCount: number
  updatedAt: string
}

/** 视图新建入参（不含 `enabled`：新建即启用；顺序由 `id` 决定） */
export interface DishViewCreateReq {
  name: string
  /** 筛选种类 ID；缺省 / `null` = 「为你推荐」（不按种类筛） */
  categoryId?: number | null
}

/** 视图修改入参：文案 / 筛选种类 / 启停**整体替换**（`name` / `enabled` 缺失 → 400） */
export interface DishViewUpdateReq {
  name: string
  categoryId?: number | null
  enabled: boolean
}

/** A6 菜品种类（独立字典表 `dish_category`；`dish.category_id` 存它的 `id`，无机器键） */
export interface DishCategoryAdminVO {
  id: number
  name: string
  /** 引用该种类的菜品数（> 0 时不可删除；本端点不提供删除） */
  dishCount: number
  updatedAt: string
}

// ===== 工单字典（举报原因 / 反馈类型，两族同形：见 web/report-options.md 与 web/feedback-options.md） =====

/**
 * 字典项出参（`OptionAdminVO`，举报原因与反馈类型共用形状）。
 *
 * <p>**数据锚在 ID**：ID 由后端生成、历史工单按它落库；`name` 只是展示文案。
 * 列表按 `id` 升序（字典不设排序位），**含已停用**项。
 */
export interface OptionAdminVO {
  /** 字典项 ID（客户端提交字段名 = `optionId`） */
  id: number
  /** 中文名（端上直接渲染） */
  name: string
  status: OnOffStatus
  /** 被同族工单记录引用次数（举报原因 = `report.option_id`；反馈类型 = `feedback.option_id`） */
  usageCount: number
  updatedAt: string
}

/** 字典项新增入参（`OptionSaveReq`：只填 `name`，ID 由后端生成；默认启用、排在末尾） */
export interface OptionSaveReq {
  /** 中文名（1~32 字） */
  name: string
}

// ===== B2 意见反馈 =====

/** 工单处置状态（以处置任务表 `moderation_task` 为判据，不由工单表自身列表达） */
export type ModerationStatus = 'pending' | 'handled'

/** 工单处置结论（`outcome` 是任务表物理列；未处置为 `null`） */
export type ModerationOutcome = 'handled' | 'rejected'

export interface FeedbackAdminVO {
  id: number
  /** 提交人 ID（**恒为真实用户 ID** —— 列带外键） */
  userId: number
  /** 提交人昵称（联表带出；注销账号显示「已注销用户」） */
  userName: string
  /** 反馈类型 ID（`feedback_option` 字典项） */
  optionId: number
  /** 反馈类型中文名（由字典实时翻译，停用类型仍可译出） */
  optionName: string
  /** 反馈正文（空串兜底） */
  content: string
  /** 配图绝对 URL 数组（≤3 张；恒为数组） */
  images: string[]
  status: ModerationStatus
  outcome: ModerationOutcome | null
  /** 处置回执短标题（未处置为 `null`） */
  replyTitle: string | null
  /** 处置文案 / 不采纳原因（未处置为 `null`；**单一列**，无独立「拒绝理由」字段） */
  replyContent: string | null
  /** 处置人登录名（未处置 / 账号已删除为 `null`） */
  handledByName: string | null
  createdAt: string
  handledAt: string | null
}

export interface FeedbackListParams {
  page?: number
  pageSize?: number
  /** `pending` 待处置 / `handled` 已处置；不传 = 全部 */
  status?: ModerationStatus | ''
  /** 按反馈类型（`feedback_option` 字典项）筛选 */
  optionId?: number
  /** 按提交人筛选 */
  userId?: number
  /** 关键词（对反馈正文 或 处置文案模糊匹配） */
  keyword?: string
}

// ===== B3 举报（独立资源，独立端点 `/admin/reports`） =====

export interface ReportAdminVO {
  id: number
  /** 举报人 ID（**恒为真实用户 ID** —— 列带外键） */
  userId: number
  /** 举报人昵称（联表带出；注销账号显示「已注销用户」） */
  userName: string
  /** 举报原因 ID（`report_option` 字典项） */
  optionId: number
  /** 举报原因中文名（由字典实时翻译，停用原因仍可译出） */
  optionName: string
  /** 被举报评价 ID；**评价已删除为 `null`** */
  reviewId: number | null
  /** 被举报评价是否已删除（`reviewId` 为 `null` 即 `true`；此时摘要与可见性均为 `null`） */
  reviewDeleted: boolean
  /** 被举报评价正文摘要（截断；评价已删除为 `null`） */
  reviewContent: string | null
  /** 被举报评价所属菜品名（评价已删除为 `null`） */
  reviewDishName: string | null
  /** 被举报评价当前是否已隐藏（决定「同时隐藏」复选是否置灰；评价已删除为 `null`） */
  reviewHidden: boolean | null
  status: ModerationStatus
  outcome: ModerationOutcome | null
  replyTitle: string | null
  replyContent: string | null
  handledByName: string | null
  createdAt: string
  handledAt: string | null
}

export interface ReportListParams {
  page?: number
  pageSize?: number
  status?: ModerationStatus | ''
  /** 按举报人筛选 */
  userId?: number
}

// ===== 工单处置请求（反馈 `PUT /admin/feedbacks/{id}`、举报 `PUT /admin/reports/{id}`、
// 菜品问题反馈驳回 `PUT /admin/corrections/{id}`（outcome 固定 rejected）同体） =====
export interface ModerationHandleReq {
  /** 回执短标题（≤40 字；留空按（类型 + 结论）用缺省文案） */
  replyTitle?: string
  /**
   * 处置文案（≤600 字）。`outcome=handled` 时可空（缺省文案）；
   * **`outcome=rejected` 时必填**（即为不采纳原因，纯空白 → 400）
   */
  replyContent?: string
  /** `handled` 受理 / 通过（**缺省**）｜ `rejected` 不予处理 / 不采纳；非法值 → 400 */
  outcome?: ModerationOutcome
  /**
   * **举报专用**：是否联动隐藏被举报评价（缺省 `false`；仅受理时消费，未受理忽略）。
   * 联动隐藏是「一次性动作」而非状态，恢复显示走评价管理。
   */
  hideReview?: boolean
}

// ===== B1 评价 =====
export interface ReviewAdminVO {
  id: number
  dishId: number
  dishName: string | null
  userId: number
  userName: string
  /** 评价者头像绝对 URL（联表带出；游客 / 未设置 / 已注销 → null，走统一占位） */
  userAvatar: string | null
  rating: number
  content: string
  images: string[]
  /** 是否被隐藏（B1 口径：**boolean**，库列 `is_hidden` 仍是 0/1，服务端翻译） */
  hidden: boolean
  /** 发表时间（`review` 无 `updated_at`：本列**仅表达首次发表时间**，覆盖提交不刷新） */
  createdAt: string
  /**
   * **最近一次可见性处置的处置人**（管理员登录名）；**未处置为 `null`**（抽屉显示 `—`）。
   *
   * <p>库列 `review.moderated_by` → `admin.id`；与 `hidden` 同生命周期、**覆盖式** ——
   * 隐藏与恢复显示**都**刷新，只反映最近一次处置（无处置历史）；删除不留痕。
   */
  moderatedByName: string | null
  /** **最近一次可见性处置的时间**（`yyyy-MM-dd HH:mm:ss`）；**未处置为 `null`** */
  moderatedAt: string | null
  /**
   * **隐藏附注**（最近一次隐藏时填写，可选）；**恢复显示 / 从未隐藏为 `null`**。
   *
   * <p>库列 `review.hidden_note`；与 `moderatedByName` / `moderatedAt` 同生命周期、覆盖式 ——
   * 供管理端复查「上一次为什么隐藏」。
   */
  hiddenNote: string | null
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

// ===== B4 菜品问题反馈 =====
export interface CorrectionAdminVO {
  id: number
  /** 问题类型：field=信息有误 / gone=已经下架（管理端据此分 Tab；两类处置动作相同） */
  type: CorrectionType
  dishId: number
  dishName: string | null
  /** 提交人 ID（提交恒带登录态 ⇒ **恒为真实用户 ID**） */
  userId: number
  /** 提交人昵称（联表带出，恒可读到；注销账号为「已注销用户」） */
  userName: string
  /** **仅 `type=gone`**：补充说明（≤200 字，处置时**必须展示**给管理员判读） */
  note: string | null
  status: CorrectionStatus
  /** 处置结论：`handled` 已受理（管理员已手动处理）/ `rejected` 不采纳；未处置为 `null` */
  outcome: ModerationOutcome | null
  /** 处置回执短标题（未处置为 `null`） */
  replyTitle: string | null
  /** 处置文案 / 不采纳原因（未处置为 `null`；单一列） */
  replyContent: string | null
  /** 处置人登录名（未处置 / 账号已删除为 `null`） */
  handledByName: string | null
  createdAt: string
  handledAt: string | null
}

/** 菜品问题反馈详情 */
export interface CorrectionDetailVO {
  id: number
  /**
   * 问题类型：`field` → 展示 `differences` 只读对照；`gone` → `differences` 恒空，
   * 只读 `note` + `images` + `goneUserCount`。两类处置动作相同（标记已处理 / 驳回）。
   */
  type: CorrectionType
  dishId: number
  dishName: string | null
  /** 目标菜品**当前**上下架状态（实时回查；菜品已物理删除为 `null`） */
  dishStatus: OnOffStatus | null
  /** 提交人 ID（提交恒带登录态 ⇒ **恒为真实用户 ID**） */
  userId: number
  /** 提交人昵称（联表带出，恒可读到；注销账号为「已注销用户」） */
  userName: string
  /** `field` 型：**只列用户提交过的项**的只读对照；`gone` 型：恒空 */
  differences: CorrectionDifference[]
  /** `gone` 型：用户选填补充说明（≤200 字） */
  note: string | null
  /** `gone` 型：同菜品待处理反馈数（已按用户去重）—— ⚠️ **仅参考，非下架阈值**（≥1 即进队列，是否下架人工决定） */
  goneUserCount: number | null
  images: string[]
  status: CorrectionStatus
  /** 处置结论：`handled` 已受理（管理员已手动处理）/ `rejected` 不采纳；未处置为 `null` */
  outcome: ModerationOutcome | null
  /** 处置回执短标题（未处置为 `null`） */
  replyTitle: string | null
  /** 处置文案 / 不采纳原因（未处置为 `null`；单一列） */
  replyContent: string | null
  /** 处置人登录名（未处置 / 账号已删除为 `null`） */
  handledByName: string | null
  createdAt: string
  handledAt: string | null
}

/** 提交项对照项（`differences[]`）：**只列用户提交过的项**，`oldValue` 为实时回查的当前值 */
export interface CorrectionDifference {
  /** 提交项键：`name` / `price` / `canteenName` / `stallName` / `floor` / `images` / `attributes.<维度ID>` */
  field: string
  /** 字段中文名（服务端下发，端上零硬编码） */
  label: string
  /** 当前实时值（回查菜品 / 档口；菜品已物理删除为 `null`） */
  oldValue: string
  /** 用户提交的值 */
  newValue: string
  /** 提交值是否与当前值一致（`true` = 已一致，端上据此标注「已一致 / 不一致」） */
  same: boolean
}

export interface CorrectionListParams {
  page?: number
  pageSize?: number
  status?: CorrectionStatus | ''
  /** 问题类型筛选：`field` / `gone` / `''`（= 全部，管理端分 Tab 用） */
  type?: CorrectionType | ''
  dishId?: number
}

/**
 * 标记已处理（接受反馈）请求：**只归档处置结论 + 回执，不改写菜品**；
 * `replyTitle` / `replyContent` 均**可选**（留空由服务端按业务措辞下发缺省回执文案）。
 */
export interface DishCorrectionHandleReq {
  /** 回执短标题（可选，≤40 字；缺省「已收到你的反馈」） */
  replyTitle?: string
  /** 处理附注（可选，≤600 字；缺省「感谢反馈，我们已核实处理」，随回执下发） */
  replyContent?: string
}

// ===== C2 用户 =====
export interface UserAdminVO {
  id: number
  /** 账号标识（微信建号为 `wx_<openid 尾 16 位>`；注销后为 `deleted_{id}`） */
  wxId: string
  name: string
  /** 头像绝对 URL（无为空串） */
  avatar: string
  status: UserStatus
  /** 是否已绑定微信（**只给布尔，不暴露 `openid` 明文**） */
  wechatBound: boolean
  /** 已认证绑定的校园邮箱（空串 = 未认证；**认证态唯一判据**） */
  email: string
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
