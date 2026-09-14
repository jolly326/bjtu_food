/**
 * 全局业务常量：集中管理散落的魔法字符串（P2-6）。
 * 与后端约定保持一致；页面/组件内优先引用此处，避免同一值在多个文件重复硬编码。
 */

/**
 * 菜品上架 / 下架状态（2026-09-14：仅用于「菜品」上下架语义）。
 * 注：食堂/档口已改为筛选属性字典，营业/停业状态不再使用本常量（Q-113/Q-115）。
 */
export const STATUS_ACTIVE = 'active'
export const STATUS_INACTIVE = 'inactive'

/**
 * 操作日志动作元数据（唯一真源，操作日志页 + 工作台「近期操作」共用）。
 * 必须与后端 `OperationLogConst` 同源：`server/src/main/java/com/bjtufood/common/constant/OperationLogConst.java`
 * （ACTION_* 常量逐个对齐；后端新增动作时此处同步补登，否则列表单元格会回落为裸英文枚举）。
 *
 * 2026-09-14（P1-03 / WEB-08）对齐结果：后端原有 12 个常量，原前端仅登记 7 项、缺 5 项。
 * - 补登：review_sec_state / category_create / category_update / category_toggle / category_delete
 * - `audit_approve` / `audit_reject`：随审核中心链路（Q-107：不新增页面、删除死代码）下线，
 *   前端不再登记（保留在常量表会形成「恒空筛选项」误导排查）。后端仍保留常量以兼容存量日志，
 *   故 actionText 对未登记值回落为原始串（存量历史日志可读，不删数据、不改后端契约）。
 */
export const OPERATION_ACTION_META: Record<string, string> = {
  review_hide: '评价隐藏',
  review_delete: '评价删除',
  review_sec_state: '安检复核',
  dish_delete: '菜品删除',
  feedback_handle: '反馈处理',
  account_delete: '账号删除',
  category_create: '品类新增',
  category_update: '品类编辑',
  category_toggle: '品类启停',
  category_delete: '品类删除',
}

/** 操作日志动作筛选下拉（'' = 全部，不透传后端） */
export const OPERATION_ACTION_OPTIONS = [
  { value: '', label: '全部动作' },
  ...Object.entries(OPERATION_ACTION_META).map(([value, label]) => ({ value, label })),
]

/**
 * 操作日志对象类型元数据（唯一真源，同上）。
 * 与后端 `targetType` 取值同源：dish / stall / canteen / category / feedback / review / user。
 */
export const OPERATION_TARGET_META: Record<string, string> = {
  dish: '菜品',
  stall: '档口',
  canteen: '食堂',
  category: '品类',
  feedback: '反馈',
  review: '评价',
  user: '用户',
}

/** 操作日志对象筛选下拉（'' = 全部，不透传后端） */
export const OPERATION_TARGET_OPTIONS = [
  { value: '', label: '全部对象' },
  ...Object.entries(OPERATION_TARGET_META).map(([value, label]) => ({ value, label })),
]

/** 动作枚举 → 中文文案（未登记值回落原始串，兼容存量历史日志，不显示空白） */
export function operationActionText(action: string): string {
  if (!action) return '—'
  return OPERATION_ACTION_META[action] ?? action
}

/** 对象类型枚举 → 中文文案（未登记值回落原始串） */
export function operationTargetText(targetType: string): string {
  if (!targetType) return '—'
  return OPERATION_TARGET_META[targetType] ?? targetType
}

/** 操作日志 target 字段（后端拼为 `targetType#targetId`）→ 可读中文；targetId 为空时只显示类型 */
export function operationTargetLabel(target: string): string {
  if (!target) return '—'
  const sep = target.indexOf('#')
  if (sep < 0) return operationTargetText(target)
  return `${operationTargetText(target.slice(0, sep))}${target.slice(sep)}`
}

/**
 * 注（§7.23 第 4 条，2026-09-15）：原 AUDIT_PENDING / AUDIT_APPROVED / AUDIT_REJECTED /
 * AUDIT_STATUS_META 已随「菜品审核 UI 下线」删除——菜品无独立审核，管理员录入即生效，
 * 客户端与后台均不出现「菜品审核」概念。dish.audit_status / reject_reason 为退役历史列，
 * 前端契约不再读写（types/Dish 与 api/adapter 已同步移除映射）。
 */

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

/**
 * 反馈类型展示文案（唯一真源，反馈列表 + 工作台「待办明细」共用）。
 * 与后端 FeedbackService 类型白名单同源：suggestion/add/error/report（历史类型 bug/other 亦可读存量）。
 */
export const FEEDBACK_TYPE_META: Record<string, string> = {
  suggestion: '功能建议',
  add: '新增菜品',
  error: '内容纠错',
  bug: '系统问题',
  report: '举报',
  other: '其他',
}

/** 反馈类型 → 中文文案（未登记值回落原始串，兼容存量脏数据） */
export function feedbackTypeText(type: string): string {
  if (!type) return '—'
  return FEEDBACK_TYPE_META[type] ?? type
}

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
