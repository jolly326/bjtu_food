import type { SecState } from '@/types'
import { get, put } from './http'
import { imagesToList, normalizeSecState, pageRecords } from './adapter'

/**
 * 反馈处理（task-09 Web · 反馈闭环 W1；prelaunch-loop-closure 收口 UGC 图片链下线）。
 * 列表 GET /admin/feedbacks（status/type/secState 过滤）；
 * 处理 PUT /admin/feedbacks/{id}（status=handled + reply）。
 * 后端出参 camelCase：FeedbackAdminVO{ id, userId, userNickname, type, content, images, contact, status, reply, createdAt, handledAt, relatedType, relatedId, secState }。
 * relatedType/relatedId 用于举报类反馈（report）关联被举报评价（review）；信息纠错（error）关联菜品（dish）。
 * images 为用户上传配图（COS 公网地址数组）；secState 为内容安检状态（评价类反馈同步展示）。
 */

export interface FeedbackAdminVO {
  id: number
  userId: number
  userNickname: string
  type: string
  content: string
  /** 用户上传配图（adapter 归一为 string[]，COS 公网地址可直接展示） */
  images: string[]
  contact: string
  status: string
  reply: string
  createdAt: string
  handledAt: string
  relatedType?: string
  relatedId?: number
  /** 内容安检状态：pass=正常 / review=待复核 / rejected=已驳回 */
  secState: SecState
}

function feedbackToLegacy(raw: any): FeedbackAdminVO {
  return {
    id: raw.id,
    userId: raw.userId ?? raw.user_id ?? 0,
    userNickname: raw.userNickname || '',
    type: raw.type || 'other',
    content: raw.content || '',
    images: imagesToList(raw.images),
    contact: raw.contact || '',
    status: raw.status || 'pending',
    reply: raw.reply || '',
    createdAt: raw.createdAt ?? raw.created_at ?? '',
    handledAt: raw.handledAt ?? raw.handled_at ?? '',
    relatedType: raw.relatedType ?? raw.related_type ?? undefined,
    relatedId: raw.relatedId ?? raw.related_id ?? undefined,
    secState: normalizeSecState(raw.secState ?? raw.sec_state),
  }
}

/** 反馈列表（分页，按 status / type / secState / userId 过滤） */
export async function listFeedbacks(params: {
  status?: string
  type?: string
  secState?: SecState | ''
  userId?: number
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<{ list: FeedbackAdminVO[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.status) query.status = params.status
  if (params.type) query.type = params.type
  if (params.secState) query.secState = params.secState
  if (params.userId != null) query.userId = params.userId
  if (params.keyword) query.keyword = params.keyword
  const data: any = await get('/admin/feedbacks', query)
  return {
    list: pageRecords(data).map(feedbackToLegacy),
    total: (data as any)?.total ?? pageRecords(data).length,
  }
}

/** 标记处理/回复：status=handled + reply + handled_at + handler_id */
export async function handleFeedback(id: number, reply: string) {
  await put<void>(`/admin/feedbacks/${id}`, { status: 'handled', reply })
}
