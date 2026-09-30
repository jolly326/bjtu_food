import { getPage, put } from './http'
import type {
  FeedbackAdminVO,
  FeedbackListParams,
  FeedbackHandleReq,
} from '@/types/common'

/** B-09 意见反馈管理：仅 type ∈ bug/suggestion/other，分页 + 筛选。 */
export function listFeedbacks(params: FeedbackListParams): Promise<FeedbackAdminVO[]> {
  return getPage<FeedbackAdminVO>('/admin/feedbacks', params)
}

export function handleFeedback(
  id: number,
  req: FeedbackHandleReq,
): Promise<FeedbackAdminVO> {
  return put<FeedbackAdminVO>(`/admin/feedbacks/${id}`, req)
}
