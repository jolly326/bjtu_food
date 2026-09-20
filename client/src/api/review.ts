import type { Review } from '@/types/review'
import { get, post, put, del } from './http'
import { recordsOf, totalOf, type RawRow, type RawPage } from './shared'

function toReview(raw: RawRow): Review {
  return {
    id: Number(raw.id),
    userId: Number(raw.userId ?? 0),
    userNickname: raw.userNickname ?? raw.userName ?? '匿名用户',
    userAvatar: raw.userAvatar || '',
    rating: Number(raw.rating || 0),
    content: raw.content || '',
    // 后端 ReviewVO 字段为 createdAt（LocalDateTime→JSON），兼容旧 createTime
    createTime: raw.createdAt || raw.createTime || '',
    // 配图（COS URL，≤3 张；后端未返回时缺省空数组，消费方按 length 渲染）
    images: Array.isArray(raw.images)
      ? (raw.images as unknown[]).filter((x): x is string => typeof x === 'string' && !!x)
      : [],
    // ===== 以下三字段仅「我的评价」返回（公开列表不含，缺省 undefined 不透传） =====
    dishId: raw.dishId != null ? Number(raw.dishId) : undefined,
    dishName: raw.dishName || '',
    isHidden: raw.isHidden != null ? !!raw.isHidden : undefined,
  }
}

/**
 * 公开评价列表（RESTful 子资源）：GET /dishes/{id}/reviews
 * - 菜品归属由路径表达（不再用查询参数）；
 * - 排序唯一为时间倒序，端上**不传 sort**（PR-02）；
 * - `hasImage=true` 时仅返回带图评价（后端 `hasImage=1`），`total` 按筛选口径统计。
 */
export async function getDishReviews(
  dishId: number,
  options?: { page?: number; pageSize?: number; hasImage?: boolean },
): Promise<{ list: Review[]; total: number }> {
  const params: Record<string, unknown> = {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? 20,
  }
  if (options?.hasImage) params.hasImage = 1
  // MP-08：响应定型为分页载体 RawPage（行结构仍宽松 → RawRow），不再用裸 any
  const res = await get<RawPage>(`/dishes/${dishId}/reviews`, params)
  return { list: recordsOf<RawRow>(res).map(toReview), total: totalOf(res) }
}

/** 删除本人评价（STU 仅本人；后端 DELETE /reviews/{id}） */
export async function deleteReview(reviewId: number): Promise<void> {
  await del<void>(`/reviews/${reviewId}`)
}

/**
 * 我的评价列表（GET /my/reviews，需邮箱认证）。
 * 行字段 = 本人视角（公开 8 字段 + dishId / dishName / isHidden），删除仍走 DELETE /reviews/{id}。
 * 传 `dishId` 时仅返回该菜本人评价——详情页据此判定「我是否已评价」并取回评价 ID（供预填 / 重评）。
 */
export async function getMyReviews(
  options?: { page?: number; pageSize?: number; dishId?: number },
): Promise<{ list: Review[]; total: number }> {
  const params: Record<string, unknown> = {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? 20,
  }
  if (options?.dishId != null) params.dishId = options.dishId
  // MP-08：同 getDishReviews，响应定型为 RawPage / RawRow
  const res = await get<RawPage>('/my/reviews', params)
  return { list: recordsOf<RawRow>(res).map(toReview), total: totalOf(res) }
}

/** 评价提交/重评入参（不含 dishId：菜品归属由路径锁定） */
interface ReviewSubmitPayload {
  /** 评分，1-5 星（必填） */
  rating: number
  /** 文字评价（≤500 字，选填） */
  content?: string
  /** 配图（COS URL，≤3 张，选填） */
  images?: string[]
}

/**
 * 发表评价（POST /dishes/{id}/reviews；需完成学号邮箱认证）。
 * 每个用户对同一菜品仅评价一次，评分 1-5 必填；归属由路径决定，请求体不含菜品 ID。
 */
export async function createReview(dishId: number, payload: ReviewSubmitPayload): Promise<void> {
  await post<void>(`/dishes/${dishId}/reviews`, payload)
}

/**
 * 重新评价（PUT /reviews/{id}；作者本人 + 需认证）。
 * **覆盖更新同一条评价**（评分 / 文字 / 配图），不新建行；发表时间刷新、隐藏标记重置、聚合重算。
 */
export async function updateReview(reviewId: number, payload: ReviewSubmitPayload): Promise<void> {
  await put<void>(`/reviews/${reviewId}`, payload)
}
