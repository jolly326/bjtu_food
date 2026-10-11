import { getAdminPage, put } from './http'
import type {
  AdminPage,
  FeedbackAdminVO,
  FeedbackListParams,
  ModerationHandleReq,
} from '@/types/common'

/**
 * 意见反馈管理（`/admin/feedbacks`）。
 *
 * <p>契约真源：[web/feedback.md](../../../docs/api/web/feedback.md)。
 * 举报是独立资源，走 `api/reports.ts`（`/admin/reports`）。
 */
export function listFeedbacks(params: FeedbackListParams): Promise<AdminPage<FeedbackAdminVO>> {
  return getAdminPage<FeedbackAdminVO>('/admin/feedbacks', params)
}

/** 处置（通过 / 不采纳；`ModerationHandleReq` 的 `hideReview` 为举报专用，本端点不消费） */
export function handleFeedback(id: number, req: ModerationHandleReq): Promise<null> {
  return put<null>(`/admin/feedbacks/${id}`, req)
}
