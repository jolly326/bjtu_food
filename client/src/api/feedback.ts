/**
 * 反馈接口模块（project_spec.md §3.x.5：POST /feedback，需 STUDENT）
 * 2026-09-07：反馈中心进度页已下线，私有 `FeedbackMyItem` / `getMyFeedback`（GET /feedback/my）删除，
 * 联动后端候选见 openspec change `client-quality-contract-cleanup` 的 backend 候选清单。
 */
import { post } from './http'
import type { FeedbackSubmit } from '@/types/feedback'

export async function submitFeedback(payload: FeedbackSubmit): Promise<void> {
  await post('/feedback', payload)
}
