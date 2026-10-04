/**
 * 反馈 / 举报写入口（三链路各自独立）：
 * - 意见反馈 `POST /feedback`（纯反馈三类型 bug/suggestion/other）
 * - 评价举报 `POST /reviews/{id}/report`（RESTful 子资源）
 * - 举报原因字典 `GET /report-reasons`（PUB）
 * - 菜品问题反馈 `POST /dishes/{id}/correction`（信息有误 / 已经下架）
 */
import { get, post } from './http'
import type { ReportReasonVO as SharedReportReasonVO } from './shared'
import type { FeedbackSubmit, ReportPayload, DishCorrectionPayload } from '@/types/feedback'

/**
 * 提交意见反馈：payload 整体透传（不逐字段映射）。
 * 仅纯反馈三类型，`content` 必填 + `images`（≤3 张）。
 */
export async function createFeedback(payload: FeedbackSubmit): Promise<void> {
  await post('/feedback', payload)
}

/**
 * 提交评价举报（举报对象在路径中）。
 * 公开可提交（游客允许）；`reason` 必选（字典 `GET /report-reasons` 下发项）；
 * 补充文本可空（填写则过安检）；被举报评价不存在 / 不可见 → 4001。
 */
export async function reportReview(reviewId: number, payload: ReportPayload): Promise<void> {
  await post(`/reviews/${reviewId}/report`, payload)
}

/**
 * 提交菜品问题反馈（`POST /dishes/{id}/correction`）。
 *
 * 请求体**必须携带 `type`**（`field` 信息有误 / `gone` 已经下架），两类字段集合不同：
 * - `field`：七字段平铺（局部提交，只传改动项；`price` 单位**分**，端上以元填写、提交前经 `yuanToFen` 转分）；
 * - `gone`：仅 `note`（≤200 字）+ `images`（≤3 张），**均可不传**（提交即成立）。
 *
 * 无 `dishId`（在路径中）。公开可提交（匿名允许）；菜品不存在 → 4001，name 敏感词 → 400 直透。
 */
export async function createDishCorrection(
  dishId: number,
  payload: DishCorrectionPayload,
): Promise<void> {
  await post(`/dishes/${dishId}/correction`, payload)
}

/** 举报原因字典项：真源 = 后端 `FeedbackConst.ReportReason`，端上直接复用生成契约，不再手写第二份副本 */
type ReportReasonVO = SharedReportReasonVO

/**
 * 端上展示模型：**必填**（契约字段可空，在此一次性兜底，避免 UI 模板处处判空）。
 * 字典项由后端静态常量构造、必带 value/label，契约的 `?` 只是 OpenAPI 对 record 分量的保守表达。
 */
export interface ReportReason {
  value: string
  label: string
}

/**
 * 举报原因字典（PUB）：举报弹层单选项，打开时实时拉取。
 * 展示顺序 = **后端下发顺序**（端上不排序）；整体透传后端数组，不做逐字段映射。
 */
export async function listReportReasons(): Promise<ReportReason[]> {
  const rows = await get<ReportReasonVO[]>('/report-reasons')
  if (!Array.isArray(rows)) return []
  return rows
    .map((r) => ({ value: String(r.value ?? ''), label: String(r.label ?? '') }))
    .filter((r) => r.value && r.label)
}
