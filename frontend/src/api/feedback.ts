/**
 * 反馈接口模块（project_spec.md §3.x.5：POST /feedback，需 STUDENT）
 */
import { post } from './http'
import type { FeedbackSubmit } from '@/types/feedback'

export async function submitFeedback(payload: FeedbackSubmit): Promise<void> {
  await post('/feedback', payload)
}
