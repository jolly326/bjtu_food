/** 审核状态（§3.x.1）：pending 待审核 / approved 已通过 / rejected 已退回 */
export type AuditStatus = 'pending' | 'approved' | 'rejected'

export interface Dish {
  id: number
  name: string
  /** 展示用「元」（API 层已由分转元） */
  price: number
  image: string
  images?: string[]
  rating: number
  ratingCount: number
  tags: string[]
  description: string
  canteen: string
  stallName: string
  isNew?: boolean
  /** 当前用户是否已评价 */
  hasReviewed?: boolean
  /** 审核状态（公开接口仅返回 approved 记录） */
  auditStatus?: AuditStatus

  /** ===== 位置链路（task-03，DishVO 扩展，来自 stall 联表） ===== */
  /** 所属档口 ID（分享深链到档口详情用） */
  stallId?: number
  /** 档口所属楼层（如 1F/2F） */
  floor?: string
  /** 窗口号 */
  windowNo?: string
  /** 档口营业时间（如 10:00-20:00） */

  businessHours?: string

  /** ===== 属性标签（task-03，DishVO 扩展，来自 dish） ===== */
  /** 辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣 */
  spiceLevel?: number
  /** 分量枚举：0=小 1=中 2=大 */
  portion?: number
  /** 供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight */
  servePeriod?: string
  /** 是否限量（0=否 1=是） */
  limited?: number | boolean
  /** 折扣价（分）：促销前原价（task-12.9，API 层已转元展示） */
  originalPrice?: number
  /** 折扣价（分，可空）：促销价，非空即视为有折扣 */
  promoPrice?: number
  /** 发布者用户 ID（task-12.5：仅本人可删除自己发布的菜品） */
  createdBy?: number
  /** 距当前用户距离（米），由后端按定位计算；缺省则来源区不展示该行 */
  distance?: number
}

export interface RatingDistribution {
  star: number
  count: number
}

export interface DishDetail extends Dish {
  ratingDistribution: RatingDistribution[]
}

export type DishSortBy = 'heat' | 'rating' | 'price' | 'created_at'

export interface DishQuery {
  keyword?: string
  /** 食堂 ID（task-02 多维筛选） */
  canteenId?: number
  /** 口味/品类标签（复用 Dish.tags，task-02 分类宫格） */
  tag?: string
  /** 辣度筛选（后端 spiceLevel 枚举 0-3：0 不辣 / 1 微辣 / 2 中辣 / 3 重辣；-1 或 undefined 表示不限） */
  spiceLevel?: number
  /** 价格区间（前端「元」，API 层转分提交） */
  minPrice?: number
  maxPrice?: number
  /** 排序维度（ARCH §3.1：heat/rating/price/created_at/collects） */
  sortBy?: DishSortBy
  sortOrder?: 'asc' | 'desc'
  page?: number
  pageSize?: number
}

/** 搜索联想项（GET /dishes/suggest，task-02；混合菜品/档口/食堂） */
export interface Suggestion {
  /** dish / stall / canteen */
  type: 'dish' | 'stall' | 'canteen'
  id: number
  name: string
  image: string
  /** 所属食堂名（仅 stall 类型返回，跳档口详情需携带 navParams.canteen） */
  canteen?: string
  /** 价格（单位：分；仅 dish 类型，展示前需转元） */
  price?: number
  /** 平均评分（仅 dish 类型） */
  rating?: number
  /** 评价数（仅 dish 类型） */
  ratingCount?: number
}

/** 热搜词（GET /dishes/hot-search，task-02；一期为菜品热度派生的热门词条） */
export interface HotSearch {
  keyword: string
  /** 热度值 */
  heat: number
  /** 关联数（菜品/档口关联数量，后端可选返回，缺省则不展示） */
  relatedCount?: number
}

/** 「我的发布」菜品（含审核态与退回原因，供审核状态页展示） */
export interface MyPublishDish {
  id: number
  name: string
  /** 单位：元（已在 api 层由分转元，勿在页面再算） */
  price: number
  image?: string
  images?: string[]
  description?: string
  tags?: string
  auditStatus?: AuditStatus
  /** audit_status=rejected 时由后台填写 */
  rejectReason?: string
  createTime?: string
}
