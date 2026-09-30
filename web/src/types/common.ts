/**
 * 管理后台通用类型与全部 VO / 请求契约（权威源：docs/web/feature/*）。
 * 后端 Java DTO/VO 出参统一 camelCase，视图层禁止手写字段映射。
 */

// ===== 统一响应信封 =====
export interface Result<T> {
  code: number
  message: string
  data: T | null
}

/** 分页壳：仅 records 一项；结束判据 = 本页条数 < 请求的 pageSize。 */
export interface PageResult<T> {
  records: T[]
}

// ===== 业务状态码 =====
export const CODE_OK = 200
export const CODE_BAD_REQUEST = 400
export const CODE_UNAUTHORIZED = 401
export const CODE_FORBIDDEN = 403
export const CODE_NOT_FOUND = 4001
export const CODE_SERVER_ERROR = 500

// ===== 通用状态枚举 =====
export type OnOffStatus = 'on' | 'off'
export type UserStatus = 'normal' | 'disabled' | 'deleted'
export type FeedbackStatus = 'pending' | 'handled'
export type CorrectionStatus = 'pending' | 'adopted' | 'rejected'

// ===== B-01 管理员 =====
export interface AdminVO {
  id: number
  username: string
  nickname: string
  role: string
  mustChangePassword: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface LoginVO {
  token: string
  expiresAt: string
  admin: AdminVO
}

// ===== B-02 运营看板 =====
export interface DashboardVO {
  pendingFeedbackCount: number
  pendingReportCount: number
  pendingCorrectionCount: number
  dishOnSaleCount: number
  dishWithImageCount: number
  dishWithReviewCount: number
  userTotal: number
  newUserToday: number
  activeUser7d: number
  reviewTotal: number
  avgRating: number | null
}

// ===== B-03 食堂 =====
export interface CanteenVO {
  id: number
  name: string
  description: string
  sortOrder: number
  status: OnOffStatus
  stallCount: number
  createdAt: string
  updatedAt: string
}

export interface CanteenSaveReq {
  name: string
  description?: string
  sortOrder?: number
}

// ===== B-04 档口 =====
export interface StallVO {
  id: number
  canteenId: number
  canteenName: string
  name: string
  floor: string
  windowNo: string
  sortOrder: number
  status: OnOffStatus
  dishCount: number
  createdAt: string
  updatedAt: string
}

export interface StallSaveReq {
  canteenId: number
  name: string
  floor?: string
  windowNo?: string
  sortOrder?: number
}

// ===== B-05 菜品 =====
export interface DishAdminVO {
  id: number
  name: string
  price: number
  originalPrice: number | null
  description: string
  images: string[]
  stallId: number
  stallName: string
  canteenId: number
  canteenName: string
  floor: string
  mealType: string
  attributes: Record<string, string | string[]>
  status: OnOffStatus
  viewCount: number
  avgRating: number | null
  ratingCount: number
  createdAt: string
  updatedAt: string
}

export interface DishSaveReq {
  name: string
  stallId: number
  price: number
  originalPrice?: number | null
  mealType: string
  description?: string
  images: string[]
  attributes?: Record<string, string | string[]>
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

// ===== B-06 属性维度 =====
export interface DishDimensionVO {
  id: number
  fieldKey: string
  name: string
  valueType: 'single' | 'multi'
  order: number
  dishCount: number
  usedValues: string[]
}

export interface DishDimensionSaveReq {
  fieldKey: string
  name: string
  valueType: 'single' | 'multi'
  order?: number
}

// ===== B-07 Banner =====
export interface BannerAdminVO {
  id: number
  imageUrl: string
  sortOrder: number
  status: OnOffStatus
  createdAt: string
  updatedAt: string
}

export interface BannerSaveReq {
  imageUrl: string
  sortOrder?: number
}

// ===== B-08 评价 =====
export interface ReviewAdminVO {
  id: number
  dishId: number
  dishName: string | null
  userId: number
  userNickname: string
  rating: number
  content: string
  images: string[]
  isHidden: boolean
  createdAt: string
}

export interface ReviewListParams {
  page?: number
  pageSize?: number
  keyword?: string
  dishId?: number
  userId?: number
  hidden?: boolean | ''
}

export interface ReviewHiddenReq {
  hidden: boolean
}

// ===== B-09 意见反馈 =====
export interface FeedbackAdminVO {
  id: number
  type: 'bug' | 'suggestion' | 'other'
  userId: number | null
  userNickname: string | null
  content: string
  images: string[]
  status: FeedbackStatus
  outcome: 'handled' | 'rejected' | null
  reply: string | null
  rejectReason: string | null
  createdAt: string
  handledAt: string | null
}

export interface FeedbackListParams {
  page?: number
  pageSize?: number
  status?: FeedbackStatus | ''
  type?: 'bug' | 'suggestion' | 'other' | ''
  keyword?: string
}

export interface FeedbackHandleReq {
  reply: string
  outcome?: 'handled' | 'rejected'
  rejectReason?: string
}

// ===== B-10 举报 =====
export interface ReportAdminVO {
  id: number
  reason: string
  reasonLabel: string
  reviewId: number
  reviewContent: string | null
  reviewDishName: string | null
  reviewHidden: boolean
  userId: number | null
  userNickname: string | null
  content: string
  images: string[]
  status: FeedbackStatus
  action: 'hideReview' | 'dismiss' | null
  reply: string | null
  createdAt: string
  handledAt: string | null
}

export interface ReportListParams {
  page?: number
  pageSize?: number
  status?: FeedbackStatus | ''
  reason?: string
}

export interface ReportHandleReq {
  action: 'hideReview' | 'dismiss'
  reply: string
}

// ===== B-11 菜品纠错 =====
export interface CorrectionAdminVO {
  id: number
  dishId: number
  dishName: string | null
  userId: number | null
  userNickname: string | null
  name: string | null
  price: number | null
  canteenName: string | null
  stallName: string | null
  floor: string | null
  attributes: Record<string, string | string[]> | null
  images: string[]
  status: CorrectionStatus
  reply: string | null
  rejectReason: string | null
  createdAt: string
  handledAt: string | null
}

export interface CorrectionListParams {
  page?: number
  pageSize?: number
  status?: CorrectionStatus | ''
  dishId?: number
}

export interface CorrectionAdoptReq {
  stallId?: number
  createIfMissing?: boolean
}

export interface StallConfirmVO {
  needStallConfirm: true
  candidates: { id: number; name: string }[]
}

export interface CorrectionRejectReq {
  reply: string
  rejectReason: string
}

// ===== B-12 用户 =====
export interface UserAdminVO {
  id: number
  nickname: string
  avatar: string
  bindEmail: string | null
  verified: boolean
  wechatBound: boolean
  status: UserStatus
  reviewCount: number
  createdAt: string
  lastLoginAt: string | null
}

export interface UserListParams {
  page?: number
  pageSize?: number
  keyword?: string
  status?: 'normal' | 'disabled' | ''
}

export interface UserStatusReq {
  disabled: boolean
}

// ===== 通用写操作请求 =====
export interface PasswordReq {
  oldPassword: string
  newPassword: string
}
