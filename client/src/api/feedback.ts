/**
 * 反馈 / 举报写入口与选项字典（三条互不耦合的链路）：
 * - 意见反馈 `POST /feedback`（类型 = 字典项 `optionId`）
 * - 评价举报 `POST /reviews/{id}/report`（RESTful 子资源，原因 = 字典项 `optionId`）
 * - 选项字典（PUB，结构同族）：反馈类型 `GET /feedback-options` · 举报原因 `GET /report-options`
 * - 菜品问题反馈 `POST /dishes/{id}/correction`（信息有误 / 已经下架）
 */
import { get, post } from './http'
import type { OptionVO as SharedOptionVO } from './shared'
import type { FeedbackSubmit, ReportPayload, DishCorrectionPayload } from '@/types/feedback'

/**
 * 提交意见反馈：payload 整体透传（不逐字段映射）。
 * `optionId` 必填且须命中 `GET /feedback-options` 启用项 + `content` 必填 + `images`（≤3 张）。
 */
export async function createFeedback(payload: FeedbackSubmit): Promise<void> {
  await post('/feedback', payload)
}

/**
 * 提交评价举报（举报对象在路径中）。
 * **需登录**（静默登录后恒有登录态，免邮箱认证）；`optionId` 必选（字典 `GET /report-options` 下发项的 `id`）；
 * 被举报评价不存在 / 不可见 → 4001。
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
 * 无 `dishId`（在路径中）。**需登录**（JWT；静默登录后恒有登录态 ⇒ 提交人恒为真实用户 ID），
 * 但**不需邮箱认证**；菜品不存在 → 4001，name 敏感词 → 400 直透。
 */
export async function createDishCorrection(
  dishId: number,
  payload: DishCorrectionPayload,
): Promise<void> {
  await post(`/dishes/${dishId}/correction`, payload)
}

/** 选项字典项：真源 = 生成契约 `OptionVO`（反馈类型与举报原因两个字典同构），端上不再手写第二份副本 */
type OptionVO = SharedOptionVO

/** 字典项端上展示模型：**必填**（契约字段可空，在此一次性兜底，避免 UI 模板处处判空） */
export interface FeedbackOption {
  id: number
  name: string
}

/** 举报原因字典项：与反馈类型字典同构，别名承载举报语义 */
export type ReportOption = FeedbackOption

/**
 * 拉取选项字典（PUB，两个字典端点同构共用一条取数路径）：
 * 逐项做 number/string 兜底收敛，非法项剔除；整体透传后端数组（端上不排序）。
 */
async function listOptions(path: string): Promise<FeedbackOption[]> {
  const rows = await get<OptionVO[]>(path)
  if (!Array.isArray(rows)) return []
  return rows
    .map((r) => ({ id: Number(r.id ?? 0), name: String(r.name ?? '') }))
    .filter((r) => r.id && r.name)
}

/** 反馈类型字典：意见反馈页类型单选项，打开页面时实时拉取 */
export async function listFeedbackOptions(): Promise<FeedbackOption[]> {
  return listOptions('/feedback-options')
}

/** 举报原因字典：举报弹层单选项，打开弹层时实时拉取 */
export async function listReportOptions(): Promise<ReportOption[]> {
  return listOptions('/report-options')
}
