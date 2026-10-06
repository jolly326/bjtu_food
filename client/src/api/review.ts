import type { Review, MyReview } from '@/types/review'
import { get, post, del } from './http'
import { DEFAULT_PAGE_SIZE } from '@/constants/paging'
import { ANONYMOUS_AUTHOR } from '@/constants/copy'
import {
  recordsOf, normalizeImages, type RawPage,
  type ReviewVO, type MyReviewVO, type ReviewCreatedVO,
} from './shared'

/**
 * 公开视角行映射（`GET /dishes/{id}/reviews`，8 字段）。
 * 不读取 `dishId` / `dishName`（属本人视角）；`isHidden` 不下发 —— 客户端只接收未隐藏评价。
 */
function toReview(raw: ReviewVO): Review {
  return {
    id: Number(raw.id),
    userId: Number(raw.userId ?? 0),
    userNickname: raw.userNickname ?? ANONYMOUS_AUTHOR,
    userAvatar: raw.userAvatar || '',
    rating: Number(raw.rating || 0),
    content: raw.content || '',
    createdAt: raw.createdAt || '',
    // 配图（COS URL，≤3 张；缺省空数组，消费方按 length 渲染）
    images: normalizeImages(raw.images),
  }
}

/**
 * 本人视角行映射（`GET /my/reviews`，7 字段）：公开 5 字段 + `dishId` / `dishName`；
 * **不含 `userId` / `userNickname` / `userAvatar`**（恒等于本人、零信息）。
 */
function toMyReview(raw: MyReviewVO): MyReview {
  return {
    id: Number(raw.id),
    rating: Number(raw.rating || 0),
    content: raw.content || '',
    createdAt: raw.createdAt || '',
    images: normalizeImages(raw.images),
    dishId: Number(raw.dishId ?? 0),
    dishName: raw.dishName || '',
  }
}

/**
 * 公开评价列表（RESTful 子资源）：GET /dishes/{id}/reviews
 * 菜品归属由路径表达；排序唯一为时间倒序，端上**不传 sort**。
 * 可选 `rating` 按星级筛选（1~5；不传 = 全部）—— **服务端过滤且参与分页**，
 * 故切换筛选后端上必须重置 `page`（见 `useDishReviewCore`）。
 * 分页壳只有 `records`：结束判据 = 本页返回条数 < `pageSize`。
 *
 * 元素类型取自生成契约（`XxxVO`），后端改字段即编译期报错。
 */
export async function listDishReviews(
  dishId: number,
  options?: { page?: number; pageSize?: number; rating?: number | null },
): Promise<{ list: Review[] }> {
  const params: Record<string, unknown> = {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? DEFAULT_PAGE_SIZE,
  }
  // 空值不传（不传 = 全部，避免把「全部」表达成 rating=0）
  if (options?.rating != null) params.rating = options.rating
  const res = await get<RawPage<ReviewVO>>(`/dishes/${dishId}/reviews`, params)
  return { list: recordsOf<ReviewVO>(res).map(toReview) }
}

/**
 * 删除本人评价（STU 仅本人；后端 DELETE /reviews/{id}）。
 * 评价不存在（含已被删除）后端返回 **4001**，由请求层抛 `ResourceNotFoundError`
 * （不弹 toast），消费方据此给「评价已不存在」的收尾处置。
 */
export async function deleteReview(reviewId: number): Promise<void> {
  await del<void>(`/reviews/${reviewId}`)
}

/**
 * 我的评价列表（GET /my/reviews，需邮箱认证）。
 * 传 `dishId` 时仅返回该菜本人评价 —— 详情页据此判定「我是否已评价」并取回评价 ID（供预填 / 重评）。
 */
export async function listMyReviews(
  options?: { page?: number; pageSize?: number; dishId?: number },
): Promise<{ list: MyReview[] }> {
  const params: Record<string, unknown> = {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? DEFAULT_PAGE_SIZE,
  }
  if (options?.dishId != null) params.dishId = options.dishId
  const res = await get<RawPage<MyReviewVO>>('/my/reviews', params)
  return { list: recordsOf<MyReviewVO>(res).map(toMyReview) }
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
 * **同一用户对同一菜品的重复提交由服务端覆盖旧评价**（端上不区分首评 / 重评）。
 * 评分 1-5 必填；归属由路径决定，请求体不含菜品 ID。
 * 成功返回评价 ID（data.id）。
 */
export async function createReview(dishId: number, payload: ReviewSubmitPayload): Promise<number> {
  const res = await post<ReviewCreatedVO>(`/dishes/${dishId}/reviews`, payload)
  return Number(res.id)
}
