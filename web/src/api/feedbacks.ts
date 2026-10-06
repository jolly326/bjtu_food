import { getAdminPage, put } from './http'
import type {
  AdminPage,
  FeedbackAdminVO,
  FeedbackListParams,
  FeedbackHandleReq,
} from '@/types/common'

/**
 * B2 / B3 反馈与举报：**同一端点**，用 `category` 分流。
 * - `category=feedback` → 意见反馈列表（B2）
 * - `category=report` → 举报列表（B3）
 */
export function listFeedbacks(params: FeedbackListParams): Promise<AdminPage<FeedbackAdminVO>> {
  return getAdminPage<FeedbackAdminVO>('/admin/feedbacks', params)
}

/** B2 处理反馈 / B3 处置举报（同一端点；举报可联动隐藏评价） */
export function handleFeedback(id: number, req: FeedbackHandleReq): Promise<null> {
  return put<null>(`/admin/feedbacks/${id}`, req)
}
