/**
 * 反馈 / 举报写入口（三链路各自独立）
 *
 * - 意见反馈：`POST /feedback`（纯反馈三类型 bug/suggestion/other）；
 * - 评价举报：`POST /reviews/{id}/report`（RESTful 子资源）；
 * - 菜品纠错：`POST /dishes/{id}/correction`（本文件下方）。
 */
import { get, post } from './http'
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
 * - 公开可提交（游客允许）；`reason` 必选（字典 `GET /feedback/report-reasons` 下发项）；
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
 * 举报原因字典项（`GET /feedback/report-reasons` 单行出参；真源 = 后端 FeedbackConst，端上零硬编码）。
 *
 * ⚠️ 端上类型**恰 2 字段**：`order` 是服务端排序用字段（后端已按升序下发，端上按返回顺序渲染）
 * → 零消费，按「零消费即删」不进入本类型。
 */
export interface ReportReason {
  value: string
  label: string
}

/**
 * 举报原因字典（PUB）：举报弹层单选项，打开时实时拉取。
 * 展示顺序 = **后端下发顺序**（端上不排序）；本函数**整体透传**后端数组，不做逐字段映射。
 */
export async function getReportReasons(): Promise<ReportReason[]> {
  const rows = await get<ReportReason[]>('/feedback/report-reasons')
  return Array.isArray(rows) ? rows : []
}
