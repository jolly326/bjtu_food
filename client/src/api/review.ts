import type { Review, ReviewSubmit, ReviewSort } from '@/types/review'
import { get, post, del } from './http'
import { recordsOf, normalizeImages } from './_shared'

type ReviewTarget =
  | { type: 'dish'; id: number }
  | { type: 'stall'; id: number }
  | { type: 'canteen'; id: number }

function toReview(raw: any): Review {
  return {
    id: Number(raw.id),
    userId: Number(raw.userId ?? 0),
    userNickname: raw.userNickname ?? raw.userName ?? '匿名用户',
    userAvatar: raw.userAvatar || '',
    dishId: Number(raw.dishId ?? 0),
    rating: Number(raw.rating || 0),
    content: raw.content || '',
    images: normalizeImages(raw.images),
    createTime: raw.createTime || '',
    // 语义统一：后端 ReviewVO.usefulCount（有用计数）
    usefulCount: Number(raw.usefulCount ?? raw.useful_count ?? 0),
    // 当前登录用户是否已标记有用（仅登录态返回）
    useful: !!raw.useful,
  }
}

/**
 * 获取评价（task-03 评价区重做）
 * 统一支持 dish / stall / canteen 三类目标查询（合并原 getReviewsByDish/Stall/Canteen 三函数）。
 * 支持 sort=latest|useful（useful 按 usefulCount DESC）、isWithImage 过滤有图。
 * 返回分页结果（list + total），供详情页评价区无限/分页展示。
 */
export async function getReviews(
  target: ReviewTarget,
  options?: { sort?: ReviewSort; isWithImage?: boolean; page?: number; pageSize?: number },
): Promise<{ list: Review[]; total: number }> {
  const params: Record<string, any> = {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? 50,
  }
  if (options?.sort) {
    params.sort = options.sort === 'latest' ? 'latest' : 'useful'
  }
  if (options?.isWithImage) params.isWithImage = true
  const res = await get<any>(`/reviews`, { [`${target.type}Id`]: target.id, ...params })
  const list = recordsOf<any>(res).map(toReview)
  const total = typeof res?.total === 'number' ? res.total : list.length
  return { list, total }
}

/** @deprecated 语义化别名，保持向后兼容。新代码请用 getReviews({ type: 'dish', id }) */
export async function getReviewsByDish(
  dishId: number,
  options?: { sort?: ReviewSort; isWithImage?: boolean; page?: number; pageSize?: number },
): Promise<{ list: Review[]; total: number }> {
  return getReviews({ type: 'dish', id: dishId }, options)
}

/** @deprecated 语义化别名，保持向后兼容。新代码请用 getReviews({ type: 'stall', id }) */
export async function getReviewsByStall(
  stallId: number,
  options?: { sort?: ReviewSort; isWithImage?: boolean; page?: number; pageSize?: number },
): Promise<{ list: Review[]; total: number }> {
  return getReviews({ type: 'stall', id: stallId }, options)
}

/** @deprecated 语义化别名，保持向后兼容。新代码请用 getReviews({ type: 'canteen', id }) */
export async function getReviewsByCanteen(
  canteenId: number,
  options?: { sort?: ReviewSort; isWithImage?: boolean; page?: number; pageSize?: number },
): Promise<{ list: Review[]; total: number }> {
  return getReviews({ type: 'canteen', id: canteenId }, options)
}

export async function submitReview(data: ReviewSubmit): Promise<void> {
  await post('/reviews', {
    dishId: data.dishId,
    rating: data.rating,
    content: data.content,
    images: data.images || [],
    shareToMoment: !!data.shareToMoment,
  })
}

/**
 * 评价「有用」切换（task-03 / ARCH §3.2）
 * 取代原非幂等的 /reviews/{id}/like。
 * POST /reviews/{id}/useful：切换 + 幂等，未标记→+1 返回 useful=true，已标记→-1 返回 useful=false。
 * 返回 { useful, usefulCount }，供前端乐观更新与回滚。
 */
export async function toggleUseful(reviewId: number): Promise<{ useful: boolean; usefulCount: number }> {
  const data = await post<{ useful: boolean; usefulCount: number }>(`/reviews/${reviewId}/useful`)
  return {
    useful: !!(data?.useful ?? false),
    usefulCount: Number(data?.usefulCount ?? 0),
  }
}

/** @deprecated 旧非幂等点赞接口已废弃，请使用 toggleUseful（/reviews/{id}/useful）。保留仅为兼容引用。 */
export async function likeReview(reviewId: number): Promise<void> {
  await toggleUseful(reviewId)
}

export async function getMyReviews(options?: { page?: number; pageSize?: number }): Promise<Review[]> {
  const params: Record<string, any> = { page: options?.page ?? 1, pageSize: options?.pageSize ?? 50 }
  const res = await get<any>(`/my/reviews`, params)
  return recordsOf<any>(res).map(toReview)
}

/** 删除本人评价（STU 仅本人，task-12.5；后端 DELETE /reviews/{id}） */
export async function deleteReview(reviewId: number): Promise<void> {
  await del<void>(`/reviews/${reviewId}`)
}
