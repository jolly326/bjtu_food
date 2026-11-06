/** 反馈域（docs/api/client/feedback.md）。 */

import { http } from '@/utils/request'

export type FeedbackType = 'product' | 'dish'

export interface FeedbackPayload {
  type: FeedbackType
  dishId?: number
  content: string
}

export const feedbackApi = {
  /** 提交产品 / 菜品文本反馈 */
  submit: (payload: FeedbackPayload) => http.post<null>('/feedback', payload),
}
