/**
 * 反馈接口模块（project_spec.md §3.x.5：POST /feedback，需 STUDENT）
 */
import { post, get } from './http'
import type { FeedbackSubmit } from '@/types/feedback'

export async function submitFeedback(payload: FeedbackSubmit): Promise<void> {
  await post('/feedback', payload)
}

/** 我的反馈项（反馈中心进度列表）
 * type 契约整改后只含枚举 suggestion/error/other/report；保留 string 兜底以兼容历史复合串记录 */
export interface FeedbackMyItem {
  id: number
  type: string
  content: string
  /** pending / handled */
  status: string
  reply?: string
  createdAt?: string
}

/** 我的反馈列表（GET /feedback/my，倒序） */
export async function getMyFeedback(): Promise<FeedbackMyItem[]> {
  const res = await get<any>('/feedback/my')
  if (Array.isArray(res)) return res as FeedbackMyItem[]
  if (Array.isArray(res?.list)) return res.list as FeedbackMyItem[]
  if (Array.isArray(res?.records)) return res.records as FeedbackMyItem[]
  return []
}
