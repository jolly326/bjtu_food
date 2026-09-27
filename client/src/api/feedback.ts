/**
 * 反馈接口模块（project_spec.md §3.x.5：POST /feedback）
 *
 * 写入口径：意见反馈页 4 类型（bug / suggestion / error / other）+ report（评价举报链路）。
 * 菜品纠错端点 `POST /dishes/{id}/correction` 端上已不再调用（2026-09-27 改版退休，端点保留）。
 */
import { get, post } from './http'
import type { FeedbackSubmit } from '@/types/feedback'

/**
 * 提交反馈：payload 整体透传（不逐字段映射），字段契约由 `FeedbackSubmit` 承载。
 * - issue：`content` 必填 + `images`（≤3 张）；
 * - report：`sub` = 举报原因机器值（必选），`content` 可空（不再强制文本描述）。
 * 「更新信息」走 `submitDishCorrection`（本文件下方）。
 */
export async function submitFeedback(payload: FeedbackSubmit): Promise<void> {
  await post('/feedback', payload)
}

/* 菜品纠错端点 `POST /dishes/{id}/correction` 端上已**不再调用**（2026-09-27 意见反馈页改单表单，
   「菜品信息纠错」改为反馈类型之一，走 `POST /feedback`）—— 端点本身保留（服务端与存量数据不动）。 */

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
