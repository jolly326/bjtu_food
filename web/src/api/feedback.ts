/** 反馈处理域（docs/api/web/feedback.md）。 */

import { http } from './request'
import { toQuery } from './query'
import type { FeedbackOut, FeedbackType, Page } from './types'

export const feedbackApi = {
  list: (params?: { type?: FeedbackType; handled?: boolean; page?: number; pageSize?: number }) =>
    http.get<Page<FeedbackOut>>(`/admin/feedback${toQuery({ ...params })}`),
  markHandled: (id: number) => http.patch<FeedbackOut>(`/admin/feedback/${id}/handled`),
}
