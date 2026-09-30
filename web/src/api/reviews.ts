import { getPage, put, del } from './http'
import type { ReviewAdminVO, ReviewListParams, ReviewHiddenReq } from '@/types/common'

/** B-08 评价管理：分页 + 筛选。 */
export function listReviews(params: ReviewListParams): Promise<ReviewAdminVO[]> {
  return getPage<ReviewAdminVO>('/admin/reviews', params)
}

export function setReviewHidden(id: number, req: ReviewHiddenReq): Promise<ReviewAdminVO> {
  return put<ReviewAdminVO>(`/admin/reviews/${id}/hidden`, req)
}

export function deleteReview(id: number): Promise<null> {
  return del<null>(`/admin/reviews/${id}`)
}
