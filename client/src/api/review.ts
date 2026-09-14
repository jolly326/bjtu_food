import type { Review } from '@/types/review'
import { get, post, del } from './http'
import { recordsOf, totalOf, type RawRow } from './shared'

type ReviewTarget =
  | { type: 'dish'; id: number }
  | { type: 'stall'; id: number }
  | { type: 'canteen'; id: number }

function toReview(raw: RawRow): Review {
  return {
    id: Number(raw.id),
    userId: Number(raw.userId ?? 0),
    userNickname: raw.userNickname ?? raw.userName ?? '匿名用户',
    userAvatar: raw.userAvatar || '',
    dishId: Number(raw.dishId ?? 0),
    rating: Number(raw.rating || 0),
    content: raw.content || '',
    // 我的评价列表由后端联表返回菜品名（菜品维度列表可不含）
    dishName: raw.dishName || '',
    // 后端 ReviewVO 字段为 createdAt（LocalDateTime→JSON），兼容旧 createTime
    createTime: raw.createdAt || raw.createTime || '',
    // 语义统一：后端 ReviewVO.usefulCount（有用计数）
    usefulCount: Number(raw.usefulCount ?? raw.useful_count ?? 0),
    // 当前登录用户是否已标记有用（仅登录态返回）
    useful: !!raw.useful,
    // 配图（COS URL，≤3 张；后端未返回时缺省空数组，消费方按 length 渲染）
    images: Array.isArray(raw.images)
      ? (raw.images as unknown[]).filter((x): x is string => typeof x === 'string' && !!x)
      : [],
    // 内容安检状态：pass=对外可见；review=机审中仅作者本人可见（缺省 pass 兼容旧响应）
    secState: raw.secState === 'review' ? 'review' : 'pass',
    // 管理侧隐藏标记（§7.14）：仅 /my/reviews 返回给作者本人（后端 isHidden，兼容 is_hidden）。
    // 语义区别于 secState：secState='review' 为机审中（待过审），isHidden=true 为已被隐藏（不再对外展示）。
    isHidden: !!(raw.isHidden ?? raw.is_hidden ?? false),
  }
}

/**
 * 获取评价（task-03 评价区重做）
 * 统一支持 dish / stall / canteen 三类目标查询（合并原 getReviewsByDish/Stall/Canteen 三函数）。
 * 排序：**端上不传 sort**——公开列表排序口径唯一权威方是后端（spec §7.14 第 2 条 / §7.18 第 3 条：
 * 默认按「有用数」置顶 `useful_count DESC, created_at DESC`），端上只消费不覆写（PR-02）。
 * 返回分页结果（list + total），供详情页评价区无限/分页展示。
 */
async function getReviews(
  target: ReviewTarget,
  options?: { page?: number; pageSize?: number },
): Promise<{ list: Review[]; total: number }> {
  const params: Record<string, unknown> = {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? 50,
  }
  const res = await get<any>(`/reviews`, { [`${target.type}Id`]: target.id, ...params })
  const list = recordsOf<any>(res).map(toReview)
  const total = totalOf(res)
  return { list, total }
}

/** @deprecated 语义化别名，保持向后兼容。新代码请用 getReviews({ type: 'dish', id }) */
export async function getReviewsByDish(
  dishId: number,
  options?: { page?: number; pageSize?: number },
): Promise<{ list: Review[]; total: number }> {
  return getReviews({ type: 'dish', id: dishId }, options)
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

/** 删除本人评价（STU 仅本人，task-12.5；后端 DELETE /reviews/{id}） */
export async function deleteReview(reviewId: number): Promise<void> {
  await del<void>(`/reviews/${reviewId}`)
}

/**
 * 我的评价列表（GET /my/reviews，STU 需邮箱认证）
 * 返回项含 dishName；删除仍走统一的 DELETE /reviews/{id}。
 * §7.14：后端不再按 is_hidden 过滤（作者本人可见自己的被隐藏评价），并返回 isHidden 供前端标注；
 * 排序由后端默认控制（按「有用数」置顶），前端不传 sort 覆写。
 */
export async function getMyReviews(options?: { page?: number; pageSize?: number }): Promise<{ list: Review[]; total: number }> {
  const res = await get<any>('/my/reviews', {
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? 20,
  })
  const list = recordsOf<any>(res).map(toReview)
  const total = totalOf(res)
  return { list, total }
}

/** 提交菜品评价（POST /reviews；需完成学号邮箱认证。每个用户对同一菜品仅评价一次，评分 1-5 必填）
 *  2026-09-07：无外部消费，收敛为模块私有（仅供本文件 createReview 入参） */
interface ReviewSubmitPayload {
  dishId: number
  /** 评分，1-5 星（必填） */
  rating: number
  /** 文字评价（≤500 字，选填） */
  content?: string
  /** 配图（COS URL，≤3 张，选填；经 /upload/images 后端安检后回传的 URL） */
  images?: string[]
}

export async function createReview(payload: ReviewSubmitPayload): Promise<void> {
  await post<void>('/reviews', payload)
}


