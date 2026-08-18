/**
 * 全局业务常量：集中管理散落的魔法字符串（P2-6）。
 * 与后端约定保持一致；页面/组件内优先引用此处，避免同一值在多个文件重复硬编码。
 */

/** 上架/营业/启用状态 */
export const STATUS_ACTIVE = 'active'
/** 下架/关闭/停用状态 */
export const STATUS_INACTIVE = 'inactive'

/** 菜品/档口/食堂审核状态 */
export const AUDIT_PENDING = 'pending'
export const AUDIT_APPROVED = 'approved'
export const AUDIT_REJECTED = 'rejected'

/** 供应时段（逗号分隔存储） */
export const SERVE_BREAKFAST = 'breakfast'
export const SERVE_LUNCH = 'lunch'
export const SERVE_DINNER = 'dinner'
export const SERVE_MIDNIGHT = 'midnight'
