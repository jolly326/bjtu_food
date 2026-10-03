import { getAdminPage, put, del } from './http'
import type {
  AdminPage,
  ReviewAdminVO,
  ReviewListParams,
  ReviewHiddenReq,
} from '@/types/common'

/** B1 评价管理：列表（排序 `createdAt DESC`） */
export function listReviews(params: ReviewListParams): Promise<AdminPage<ReviewAdminVO>> {
  return getAdminPage<ReviewAdminVO>('/admin/reviews', params)
}

/**
 * B1 设置隐藏 / 恢复显示（显式置位，非 toggle）。
 * 隐藏时可带**可选附注** `note`（≤200 字），随站内回执下发给评价作者。
 */
export function setReviewHidden(id: number, req: ReviewHiddenReq): Promise<null> {
  return put<null>(`/admin/reviews/${id}/hidden`, req)
}

/** B1 删除（物理删除 + 触发评分重算 + 向作者投递回执） */
export function deleteReview(id: number): Promise<null> {
  return del<null>(`/admin/reviews/${id}`)
}
