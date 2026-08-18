import { get, put } from './http'
import { pageRecords } from './adapter'

/**
 * 反馈处理（task-09 Web · 反馈闭环 W1）。
 * 列表 GET /admin/feedbacks（status/type 过滤）；
 * 处理 PUT /admin/feedbacks/{id}（status=handled + reply）。
 * 后端出参 camelCase：FeedbackAdminVO{ id, userId, userNickname, type, content, contact, status, reply, createdAt, handledAt, relatedType, relatedId }。
 * relatedType/relatedId 用于举报类反馈（report）关联被举报动态（moment）。
 */

/** 安全解析反馈附图：兼容 JSON 数组字符串 / 逗号分隔字符串 / 数组 / 空值，解析失败兜底空数组 */
function parseImages(images: unknown): string[] | undefined {
  if (Array.isArray(images)) return images.filter(Boolean)
  if (typeof images !== 'string') return undefined
  const trimmed = images.trim()
  if (!trimmed) return undefined
  try {
    const parsed = JSON.parse(trimmed)
    return Array.isArray(parsed) ? parsed.filter(Boolean) : [trimmed]
  } catch {
    // 非 JSON 格式（如逗号分隔），按分隔符拆分兜底
    return trimmed.split(',').map((s) => s.trim()).filter(Boolean)
  }
}

export interface FeedbackAdminVO {
  id: number
  userId: number
  userNickname: string
  type: string
  content: string
  contact: string
  status: string
  reply: string
  createdAt: string
  handledAt: string
  relatedType?: string
  relatedId?: number
  /** 附图（绝对URL数组，2026-08-17 新增） */
  images?: string[]
}

function feedbackToLegacy(raw: any): FeedbackAdminVO {
  return {
    id: raw.id,
    userId: raw.userId ?? raw.user_id ?? 0,
    userNickname: raw.userNickname || '',
    type: raw.type || 'other',
    content: raw.content || '',
    contact: raw.contact || '',
    status: raw.status || 'pending',
    reply: raw.reply || '',
    createdAt: raw.createdAt ?? raw.created_at ?? '',
    handledAt: raw.handledAt ?? raw.handled_at ?? '',
    relatedType: raw.relatedType ?? raw.related_type ?? undefined,
    relatedId: raw.relatedId ?? raw.related_id ?? undefined,
    images: parseImages(raw.images),
  }
}

/** 反馈列表（分页，按 status / type / userId 过滤） */
export async function listFeedbacks(params: {
  status?: string
  type?: string
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
