/**
 * 全局业务常量：集中管理散落的魔法字符串（P2-6）。
 * 与后端约定保持一致；页面/组件内优先引用此处，避免同一值在多个文件重复硬编码。
 */

/**
 * 操作日志动作元数据（唯一真源，操作日志页消费）。
 * 必须与后端 `OperationLogConst` 同源：`server/src/main/java/com/bjtufood/common/constant/OperationLogConst.java`
 * （ACTION_* 常量逐个对齐；后端新增动作时此处同步补登，否则列表单元格会回落为裸英文枚举）。
 *
 * 2026-09-14（P1-03 / WEB-08）对齐结果：后端原有 12 个常量，原前端仅登记 7 项、缺 5 项。
 * - 补登：安检复核动作及字典域 4 项（字典域维护入口已整链删除，本次同步移除，见下）。
 * - `audit_approve` / `audit_reject`：随审核中心链路（Q-107：不新增页面、删除死代码）下线，
 *   前端不再登记（保留在常量表会形成「恒空筛选项」误导排查）。后端仍保留常量以兼容存量日志，
 *   故 actionText 对未登记值回落为原始串（存量历史日志可读，不删数据、不改后端契约）。
 * - 2026-09-15：字典域（后台已无维护入口）的 4 个动作与对应 targetType 一并移除，理由同上——
 *   后台已无产生该动作的入口，登记后会形成「恒空筛选项」误导排查；存量历史日志仍按原始串展示。
 * - 2026-09-15（取消人工复核）：安检复核动作随后台人工复核职责取消一并移除，理由同上——
 *   内容机检的放行态与待复核态均对客户端放行、仅风险项拒绝，后台已无产生该动作的入口。
 */
// 仅本文件自用（外部消费出口为 OPERATION_ACTION_OPTIONS / operationActionText），故不单独导出。
const OPERATION_ACTION_META: Record<string, string> = {
  review_hide: '评价隐藏',
  review_delete: '评价删除',
  dish_delete: '菜品删除',
  feedback_handle: '反馈处理',
  account_delete: '账号删除',
}

/** 操作日志动作筛选下拉（'' = 全部，不透传后端） */
export const OPERATION_ACTION_OPTIONS = [
  { value: '', label: '全部动作' },
  ...Object.entries(OPERATION_ACTION_META).map(([value, label]) => ({ value, label })),
]

/**
 * 操作日志对象类型元数据（唯一真源，同上）。
 * 与后端 `targetType` 取值同源：dish / stall / canteen / feedback / review / user
 * （字典域 targetType 随该域维护入口下线一并移除，存量日志按原始串展示）。
 */
// 同上：仅本文件自用（外部消费出口为 OPERATION_TARGET_OPTIONS / operationTargetText）。
const OPERATION_TARGET_META: Record<string, string> = {
  dish: '菜品',
  stall: '档口',
  canteen: '食堂',
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

/**
 * 注（2026-09-15 拍板：取消人工复核）：原「内容安检状态」三态常量（正常 / 待复核 / 已驳回）、
 * 其展示元数据与筛选下拉选项已整体删除——内容机检的放行态与待复核态均对客户端放行、
 * 仅风险项拒绝，后台已无人工复核动作，评价管理 / 反馈处理两页均不展示、不筛选安检状态
 * （后端字段同源移除）。保留在此会形成「恒空筛选项」误导排查，故不留空壳映射。
 */

/**
 * 反馈类型展示文案（唯一真源，反馈列表消费）。
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
