/**
 * 全局业务常量：集中管理散落的魔法字符串（P2-6）。
 * 与后端约定保持一致；页面/组件内优先引用此处，避免同一值在多个文件重复硬编码。
 */

/**
 * 注（2026-09-15，后台精简）：原「操作日志」动作 / 对象元数据（OPERATION_ACTION_META、
 * OPERATION_ACTION_OPTIONS、OPERATION_TARGET_META、OPERATION_TARGET_OPTIONS、
 * operationActionText / operationTargetText / operationTargetLabel）已整体删除——
 * 操作日志页（views/system/OperationLogView.vue）与其 API 层（api/operationLog.ts）同轮下线，
 * 全站零消费（grep 复核通过）。后端 AOP 埋点与存量日志数据均不受影响，仅前端不再展示。
 * 退役后如需恢复，可从 git 历史取回本文件对应段落。
 */
/**
 * 注（§7.23 第 4 条，2026-09-15）：原 AUDIT_PENDING / AUDIT_APPROVED / AUDIT_REJECTED /
 * AUDIT_STATUS_META 已随「菜品审核 UI 下线」删除——菜品无独立审核，管理员录入即生效，
 * 客户端与后台均不出现「菜品审核」概念。dish.audit_status / reject_reason 为退役历史列，
 * 前端契约不再读写（types/Dish 与 api/adapter 已同步移除映射）。
 */

/**
 * 注（2026-09-15 拍板：取消人工复核）：原「内容安检状态」三态常量（正常 / 待复核 / 已驳回）、
 * 其展示元数据与筛选下拉选项已整体删除——内容安全检测的放行态与待复核态均对客户端放行、
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

/* ============================================================
 * 菜品「描述四维」（§7.28 描述维度替换，2026-09-20）
 *
 * 权威依据：project_spec.md §7.28 / docs/database.md dish 表四列注释。
 * 四维替代原「辣度（spice_level）」与「风味/菜系（region）」——机器值 → 中文映射
 * 唯一真源集中在此（Web 端），表单录入与详情展示均经此处，视图层禁止二次映射。
 * 机器值必须与后端契约 / client 映射一致（写入错误值会导致端上无法识别）。
 * ============================================================ */

/** 多值机器字段（ingredients / flavorTags）读写格式：CSV 逗号分隔串（同原 tags 模式） */
export function parseCsv(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(v => String(v).trim()).filter(Boolean)
  const s = typeof value === 'string' ? value.trim() : ''
  if (!s) return []
  // 容错兼容历史脏数据（旧实现曾误写 JSON 数组串），展示时自动归一
  if (s.startsWith('[')) {
    try {
      const parsed = JSON.parse(s)
      if (Array.isArray(parsed)) return parsed.map(v => String(v).trim()).filter(Boolean)
    } catch { /* 非合法 JSON，按 CSV 继续解析 */ }
  }
  return s.split(',').map(v => v.trim()).filter(Boolean)
}

/** string[] → CSV 逗号分隔串（写库格式，写侧统一出口） */
export function formatCsv(list: string[]): string {
  return list.map(v => v.trim()).filter(Boolean).join(',')
}

/** 荤素 / 饮食属性（单选）：机器值 → 中文 */
export const DIET_TYPE_META: Record<string, string> = {
  meat: '荤',
  half: '半荤',
  veg: '素',
  halal: '清真',
}
/** 荤素下拉选项（含「未填写」空值项，不提交时该维留空） */
export const DIET_TYPE_OPTIONS = [
  { value: '', label: '未填写' },
  ...Object.entries(DIET_TYPE_META).map(([value, label]) => ({ value, label })),
]
/** 荤素 → 中文（空值回落「—」，未知值原样透出，不吞不译） */
export function dietTypeText(v?: string): string {
  if (!v) return '—'
  return DIET_TYPE_META[v] ?? v
}

/** 冷热（单选）：机器值 → 中文 */
export const SERVE_TEMP_META: Record<string, string> = {
  hot: '热食',
  room: '常温',
  ice: '冰',
}
/** 冷热下拉选项（含「未填写」空值项） */
export const SERVE_TEMP_OPTIONS = [
  { value: '', label: '未填写' },
  ...Object.entries(SERVE_TEMP_META).map(([value, label]) => ({ value, label })),
]
/** 冷热 → 中文 */
export function serveTempText(v?: string): string {
  if (!v) return '—'
  return SERVE_TEMP_META[v] ?? v
}

/** 主料 / 食材（多选）：机器值 → 中文 */
export const INGREDIENT_META: Record<string, string> = {
  pork: '猪',
  beef: '牛',
  lamb: '羊',
  chicken: '鸡',
  duck: '鸭',
  fish: '鱼虾',
  egg: '蛋',
  tofu: '豆制品',
  mushroom: '菌菇',
  veg: '青菜',
  noodle: '面',
  rice: '米',
}
/** 主料多选项（无空值项；未选即不提交 / 留空） */
export const INGREDIENT_OPTIONS = Object.entries(INGREDIENT_META).map(([value, label]) => ({ value, label }))
/** 主料 CSV → 中文「 · 」连接（空 = 「—」） */
export function ingredientsText(csv?: string): string {
  const arr = parseCsv(csv)
  return arr.length ? arr.map(v => INGREDIENT_META[v] ?? v).join(' · ') : '—'
}

/** 口味（多选）：机器值 → 中文 */
export const FLAVOR_TAG_META: Record<string, string> = {
  spicy: '辣',
  numbing: '麻',
  sour: '酸',
  sweet: '甜',
  salty: '咸',
  umami: '鲜',
  light: '清淡',
  heavy: '重口',
}
/** 口味多选项 */
export const FLAVOR_TAG_OPTIONS = Object.entries(FLAVOR_TAG_META).map(([value, label]) => ({ value, label }))
/** 口味 CSV → 中文「 · 」连接（空 = 「—」） */
export function flavorTagsText(csv?: string): string {
  const arr = parseCsv(csv)
  return arr.length ? arr.map(v => FLAVOR_TAG_META[v] ?? v).join(' · ') : '—'
}
