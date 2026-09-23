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
 * 菜品「描述四维」的**读写形态工具**（§7.28 描述维度替换，2026-09-20）
 *
 * ⚠️ 2026-09-23 迁移（§7.40 R4）：四维的「机器值 → 中文」**映射表与表单选项数组已整体删除**，
 * 改由后端字典端点 `GET /dishes/attributes`（`DishAttributeConst`）下发，Web 端消费方为
 * `stores/dishAttributeStore.ts` —— 展示走 `labelOf` / `labelsText`，录入走 `optionsOf`。
 * **本文件与任何视图均不得再硬编码四维的 `value → 中文` 映射或选项清单**（PR-12）。
 * 迁移缘由：改动前 Web 端在此硬编码 4 张 `*_META` + 4 组 `*_OPTIONS`（同时兼作表单选项），
 * 与 client 端 `api/dish.ts` 的 4 张表构成**两套前端真源**（连同后端常量表共三套）。
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
 * 已删除（2026-09-23 §7.40 R4）：DIET_TYPE_META / DIET_TYPE_OPTIONS / dietTypeText /
 * SERVE_TEMP_META / SERVE_TEMP_OPTIONS / serveTempText / INGREDIENT_META / INGREDIENT_OPTIONS /
 * ingredientsText / FLAVOR_TAG_META / FLAVOR_TAG_OPTIONS / flavorTagsText。
 *
 * 上述 4 张「机器值 → 中文」映射表与 4 组表单选项数组**整体退役** —— 其内容与后端
 * `DishAttributeConst` 经 `GET /dishes/attributes` 下发的字典完全重复，属**前端自建枚举真源**
 * （违反 PR-12）。消费方迁移：
 *   - 展示（`DishDetailView.vue` 描述四维）→ `useDishAttributeStore().labelOf / labelsText`
 *   - 录入（`DishFormDialog.vue` 下拉与 chips）→ `useDishAttributeStore().optionsOf`
 * 退役后如需追溯旧值域，见 `project_spec.md` §7.28（值域真源）与 git 历史。
 */

