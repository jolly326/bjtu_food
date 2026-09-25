/**
 * 全局业务常量：集中管理散落的魔法字符串（P2-6）。
 * 与后端约定保持一致；页面/组件内优先引用此处，避免同一值在多个文件重复硬编码。
 */

/**
 * 操作日志：后端 AOP 埋点仍照常写，前端不再展示（无操作日志页 / API）。
 */
/**
 * 菜品无独立审核（§7.23 第 4 条）：管理员录入即生效，客户端与后台均不出现「菜品审核」概念。
 * dish.audit_status / reject_reason 为后端历史列，前端契约不再读写。
 */

/**
 * 评价 / 反馈无「内容安检状态」字段与复核动作：内容安全检测放行态与待复核态均对客户端放行、
 * 仅风险项拒绝，评价管理 / 反馈处理两页均不展示、不筛选安检状态（后端字段同源移除）。
 */

/**
 * 反馈类型展示文案（唯一真源，反馈列表消费）。
 * 拆分信息纠错：「信息更新」类型拆出为独立资源
 * /admin/corrections，本表不再登记该值（web 域 grep 该类型机器值应为 0）；
 * 现值域 = issue（现写）+ 历史存量类型（筛选兼容，未登记值回落原始串）。
 */
export const FEEDBACK_TYPE_META: Record<string, string> = {
  issue: '问题反馈',
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
 * 信息纠错（自反馈拆分，独立处理页 CorrectionView 消费）
 * ============================================================ */

/** 纠错状态：待处理 */
export const CORRECTION_PENDING = 'pending'
/** 纠错状态：已采纳（快照已写回目标菜品） */
export const CORRECTION_ADOPTED = 'adopted'
/** 纠错状态：已拒绝（回复 + 拒绝原因必填） */
export const CORRECTION_REJECTED = 'rejected'

/**
 * 纠错状态展示元数据（StatusTag 类型 + 文案）。
 * 与后端 CorrectionAdminVO.status 契约一致（pending / adopted / rejected）。
 */
export const CORRECTION_STATUS_META: Record<string, { type: 'warning' | 'success' | 'danger'; text: string }> = {
  [CORRECTION_PENDING]: { type: 'warning', text: '待处理' },
  [CORRECTION_ADOPTED]: { type: 'success', text: '已采纳' },
  [CORRECTION_REJECTED]: { type: 'danger', text: '已拒绝' },
}

/* ============================================================
 * 菜品「描述四维」的**读写形态工具**（§7.28 描述维度替换）
 *
 * 四维的「机器值 → 中文」映射表与表单选项数组由后端字典端点 `GET /dishes/attributes`
 * （`DishAttributeConst`）下发，Web 端消费方为 `stores/dishAttributeStore.ts` ——
 * 展示走 `labelOf` / `labelsText`，录入走 `optionsOf`。
 * **本文件与任何视图均不得再硬编码四维的 `value → 中文` 映射或选项清单**（PR-12）。
 *
 * 下方 `parseCsv` / `formatCsv` 是**读写格式工具**（多值字段的 CSV / 数组归一），
 * 与「映射真源」无关，故保留（R4 数组化后仍可复用）。
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

/*
 * 四维「机器值 → 中文」映射表与表单选项数组（DIET_TYPE / SERVE_TEMP / INGREDIENT / FLAVOR_TAG
 * 的 `*_META` / `*_OPTIONS` / `*Text`）不再内置，统一由后端字典端点 `GET /dishes/attributes`
 * 下发（PR-12，违反则构成前端自建枚举真源）。消费方：
 *   - 展示（`DishDetailView.vue` 描述四维）→ `useDishAttributeStore().labelOf / labelsText`
 *   - 录入（`DishFormDialog.vue` 下拉与 chips）→ `useDishAttributeStore().optionsOf`
 */

