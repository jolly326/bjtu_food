import type { Review, MyReview } from '@/types/review'
import { get, post, del } from './http'
import { DEFAULT_PAGE_SIZE } from '@/constants/paging'
import {
  recordsOf, type RawPage,
  type ReviewVO, type MyReviewVO, type ReviewCreatedVO,
} from './shared'

/**
 * 公开视角行映射（`GET /dishes/{id}/reviews`，8 字段）。
 * R9 拆型：本函数**不再**读取 `dishId` / `dishName`（二者属本人视角；`isHidden` 任何视角均不下发，客户端只接收未隐藏评价）。
 *
 * <p>入参用生成的强类型 {@link ReviewVO}。
 */
function toReview(raw: ReviewVO): Review {
  return {
    id: Number(raw.id),
    userId: Number(raw.userId ?? 0),
    userNickname: raw.userNickname ?? '匿名用户',
    userAvatar: raw.userAvatar || '',
    rating: Number(raw.rating || 0),
    content: raw.content || '',
    // 时间字段统一为 createdAt，不使用 createTime 别名
    createdAt: raw.createdAt || '',
    // 配图（COS URL，≤3 张；后端未返回时缺省空数组，消费方按 length 渲染）
    images: Array.isArray(raw.images)
      ? (raw.images as unknown[]).filter((x): x is string => typeof x === 'string' && !!x)
      : [],
  }
}

/**
 * 本人视角行映射（`GET /my/reviews`，**7 字段**）。
 *
 * 后端出参类型为 `MyReviewVO`：公开 5 字段（`id` / `rating` / `content` / `images` / `createdAt`）
 * + `dishId` / `dishName`；**不含 `userId` / `userNickname` / `userAvatar`**（恒等于本人、零信息）。
 */
function toMyReview(raw: MyReviewVO): MyReview {
  return {
    id: Number(raw.id),
    rating: Number(raw.rating || 0),
    content: raw.content || '',
    createdAt: raw.createdAt || '',
    images: Array.isArray(raw.images)
      ? (raw.images as unknown[]).filter((x): x is string => typeof x === 'string' && !!x)
      : [],
    dishId: Number(raw.dishId ?? 0),
    dishName: raw.dishName || '',
  }
}

/**
 * 公开评价列表（RESTful 子资源）：GET /dishes/{id}/reviews
 * - 菜品归属由路径表达（不再用查询参数）；
 * - 排序唯一为时间倒序，端上**不传 sort**（PR-02）；
 * - 分页壳只有 `records`：结束判据 = 本页返回条数 < `pageSize`。
 */
export async function listDishReviews(
  dishId: number,
  options?: { page?: number; pageSize?: number },
): Promise<{ list: Review[] }> {
  const params: Record<string, unknown> = {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? DEFAULT_PAGE_SIZE,
  }
  // 强类型：元素类型取自生成契约，后端改 ReviewVO 字段即编译期报错
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
 * 行字段 = **本人视角 7 字段**；删除仍走 DELETE /reviews/{id}。
 * 传 `dishId` 时仅返回该菜本人评价——详情页据此判定「我是否已评价」并取回评价 ID（供预填 / 重评）。
 */
export async function listMyReviews(
  options?: { page?: number; pageSize?: number; dishId?: number },
): Promise<{ list: MyReview[] }> {
  const params: Record<string, unknown> = {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? DEFAULT_PAGE_SIZE,
  }
  if (options?.dishId != null) params.dishId = options.dishId
  // 强类型：同 listDishReviews，元素类型取自生成契约
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
 * **同一用户对同一菜品的重复提交由服务端覆盖旧评价**（端上不区分首评 / 重评，简化），
 * 故不再提供 `PUT /reviews/{id}` 的端上封装（零调用即删）。评分 1-5 必填；归属由路径决定，请求体不含菜品 ID。
 * 成功返回评价 ID（data.id）。
 */
export async function createReview(dishId: number, payload: ReviewSubmitPayload): Promise<number> {
  const res = await post<ReviewCreatedVO>(`/dishes/${dishId}/reviews`, payload)
  return Number(res.id)
}
