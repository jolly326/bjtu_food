/**
 * 反馈接口模块（project_spec.md §3.x.5：POST /feedback）
 *
 * 写入口径收敛为 issue（反馈问题）+ report（评价举报链路）。
 * 「更新信息」走独立纠错端点 `POST /dishes/{id}/correction`（公开可提交，匿名允许，无需登录守卫）。
 */
import { get, post } from './http'
import type { FeedbackSubmit, DishCorrectionPayload } from '@/types/feedback'

/**
 * 提交反馈：payload 整体透传（不逐字段映射），字段契约由 `FeedbackSubmit` 承载。
 * - issue：`content` 必填 + `images`（≤3 张）；
 * - report：`sub` = 举报原因机器值（必选），`content` 可空（不再强制文本描述）。
 * 「更新信息」走 `submitDishCorrection`（本文件下方）。
 */
export async function submitFeedback(payload: FeedbackSubmit): Promise<void> {
  await post('/feedback', payload)
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

/** 举报原因字典项（`GET /feedback/report-reasons` 单行出参；真源 = 后端 FeedbackConst，端上零硬编码） */
export interface ReportReason {
  value: string
  label: string
  order: number
}

/**
 * 举报原因字典（PUB）：举报弹层单选项，打开时实时拉取。
 * 展示顺序 = 后端下发顺序（order 升序）。
 */
export async function getReportReasons(): Promise<ReportReason[]> {
  const rows = await get<ReportReason[]>('/feedback/report-reasons')
  return Array.isArray(rows) ? rows : []
}
