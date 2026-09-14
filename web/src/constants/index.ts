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

/** 内容安检状态：正常（评价/反馈共用，与后端 ReviewAdminVO.secState 契约一致） */
export const SEC_PASS = 'pass'
/** 内容安检状态：待复核 */
export const SEC_REVIEW = 'review'
/** 内容安检状态：已驳回 */
export const SEC_REJECTED = 'rejected'

/** 安检状态展示元数据（StatusTag 类型 + 文案）：评价审核 / 菜品评论 / 反馈三视图共用 */
export const SEC_STATE_META: Record<string, { type: 'success' | 'warning' | 'danger'; text: string }> = {
  [SEC_PASS]: { type: 'success', text: '正常' },
  [SEC_REVIEW]: { type: 'warning', text: '待复核' },
  [SEC_REJECTED]: { type: 'danger', text: '已驳回' },
}

/** 安检状态筛选下拉选项（'' = 全部，不透传后端）；「待复核」紧随全部，便于一键进入复核队列 */
export const SEC_FILTER_OPTIONS = [
  { value: '', label: '全部安检状态' },
  { value: SEC_REVIEW, label: '待复核' },
  { value: SEC_PASS, label: '正常' },
  { value: SEC_REJECTED, label: '已驳回' },
]

/** 反馈处理状态：待处理 */
export const FEEDBACK_PENDING = 'pending'
/** 反馈处理状态：已处理 */
export const FEEDBACK_HANDLED = 'handled'

/**
 * 反馈处理状态展示元数据（StatusTag 类型 + 文案）。
 * 与后端 FeedbackAdminVO.status 契约一致（pending / handled）。
 */
export const FEEDBACK_STATUS_META: Record<string, { type: 'warning' | 'success'; text: string }> = {
  [FEEDBACK_PENDING]: { type: 'warning', text: '待处理' },
  [FEEDBACK_HANDLED]: { type: 'success', text: '已处理' },
}
