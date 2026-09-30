/**
 * 反馈 / 举报写入口（三链路各自独立）
 *
 * - 意见反馈：`POST /feedback`（纯反馈三类型 bug/suggestion/other）；
 * - 评价举报：`POST /reviews/{id}/report`（RESTful 子资源）；
 * - 举报原因字典：`GET /report-reasons`（PUB。2026-09-30 P2 自 `/feedback/report-reasons` 迁出）；
 * - 菜品纠错：`POST /dishes/{id}/correction`（本文件下方）。
 */
import { get, post } from './http'
import type { ReportReasonVO as SharedReportReasonVO } from './shared'
import type { FeedbackSubmit, ReportPayload, DishCorrectionPayload } from '@/types/feedback'

/**
 * 提交意见反馈：payload 整体透传（不逐字段映射）。
 * 仅纯反馈三类型（bug/suggestion/other），`content` 必填 + `images`（≤3 张）。
 */
export async function submitFeedback(payload: FeedbackSubmit): Promise<void> {
  await post('/feedback', payload)
}

/**
 * 提交评价举报：`POST /reviews/{id}/report`（RESTful 子资源，举报对象在路径中）。
 * - 公开可提交（游客允许）；`reason` 必选（字典 `GET /report-reasons` 下发项）；
 * - 补充文本可空（填写则过安检）；被举报评价不存在 / 不可见 → 4001。
 */
export async function reportReview(reviewId: number, payload: ReportPayload): Promise<void> {
  await post(`/reviews/${reviewId}/report`, payload)
}

/**
 * 提交菜品纠错（意见反馈页「我要更新信息」）：`POST /dishes/{id}/correction`。
 * - dishId 在路径中；请求体七字段平铺（无 payload 包裹、无 type、无 dishId 字段）；
 * - price 单位 = 分（端上以元填写，提交前经 yuanToFen 转分，金额红线）；
 * - 公开可提交（匿名允许）；菜品不存在 → 4001，name 敏感词 → 400 message 直透。
 */
export async function submitDishCorrection(
  dishId: number,
  payload: DishCorrectionPayload,
): Promise<void> {
  await post(`/dishes/${dishId}/correction`, payload)
}

/**
 * 举报原因字典项（`GET /report-reasons` 出参；真源 = 后端 `FeedbackConst.ReportReason` 记录类）。
 *
 * <p><b>直接复用生成契约</b>（2026-09-29）：原手写的 `interface ReportReason { value; label }`
 * 与契约 `ReportReasonVO` 字段完全一致，属**同一端点的第二份类型副本**——两份必然漂移。
 * 现以上游取契约、在此一次性归一为端上必填模型。
 *
 * <p>契约恰 2 字段：后端 {@code record ReportReason(String value, String label)}，
 * 顺序由 `List` 声明次序表达，端上按返回顺序渲染、不排序。
 */
type ReportReasonVO = SharedReportReasonVO

/**
 * 端上展示模型：**必填**（契约字段可空，此处归一）。
 *
 * <p>后端 record 的两个字段理论上可空，但字典项由 `FeedbackConst` 静态常量构造、
 * 必带 value/label。契约的 `?` 只是 OpenAPI 对 record 分量的保守表达。
 * 与其让 UI 模板处处判空（`r.value!`），不如在此**一次性兜底**——
 * 与 dish/review/user 各模块的 `|| ''` 归一策略保持一致。
 */
export interface ReportReason {
  value: string
  label: string
}

/**
 * 举报原因字典（PUB）：举报弹层单选项，打开时实时拉取。
 * 展示顺序 = **后端下发顺序**（端上不排序）；本函数**整体透传**后端数组，不做逐字段映射。
 *
 * <p>入参用契约 `ReportReasonVO`（2026-09-29）：后端 record 的分量理论上可空，
 * 但字典由 `FeedbackConst` 静态常量构造、必带 value/label，
 * 故在 api 层统一兜底成端上必填，避免 UI 模板处处判空。
 */
export async function getReportReasons(): Promise<ReportReason[]> {
  const rows = await get<ReportReasonVO[]>('/report-reasons')
  if (!Array.isArray(rows)) return []
  // 契约字段可空 → 端上必填：一次性归一，避免 UI 模板处处判空
  return rows
    .map((r) => ({ value: String(r.value ?? ''), label: String(r.label ?? '') }))
    .filter((r) => r.value && r.label)
}
