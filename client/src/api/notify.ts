/**
 * 消息通知接口模块（task-09，ARCH §3.4，STU）
 *
 * GET /my/notifications         我的消息（倒序，isRead 过滤）
 * GET /my/notifications/unread-count 未读总数（红点）
 * PUT /my/notifications/{id}/read  单条已读
 * PUT /my/notifications/read-all   全部已读（幂等，需登录，返回本次置为已读的条数）
 */
import { get, put } from './http'
import { recordsOf, type PageResult, type RawRow } from './shared'

/** 2026-09-07：无外部消费，收敛为模块私有（仅本文件 toNotification/Notification 使用）。
 *  2026-09-12：新增 feedback_handle（反馈处理结果回执），与后端 NotificationConst 对齐。 */
type NotificationType = 'dish_audit' | 'feedback_handle'

export interface Notification {
  id: number
  /** 通知类型：dish_audit=菜品审核结果；feedback_handle=反馈处理结果回执 */
  type: NotificationType
  title: string
  content: string
  /** 关联对象 ID（按 type 解释：dish_audit=菜品 ID；feedback_handle=反馈 ID） */
  relatedId?: number | null
  /** 是否已读：0=未读 1=已读 */
  isRead: number
  createdAt?: string
}

function toNotification(raw: RawRow): Notification | null {
  if (!raw) return null
  return {
    id: Number(raw.id),
    type: (raw.type as NotificationType) || 'dish_audit',
    title: raw.title || '',
    content: raw.content || '',
    relatedId: raw.relatedId ?? null,
    isRead: Number(raw.isRead ?? 0),
    createdAt: raw.createdAt,
  }
}

/** 我的消息列表（STU，倒序） */
export async function getNotifications(params: {
  isRead?: 0 | 1
  page?: number
  pageSize?: number
}): Promise<{ list: Notification[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.isRead != null) query.isRead = params.isRead
  const res = await get<PageResult<RawRow>>('/my/notifications', query)
  const raw = recordsOf(res).map(toNotification).filter(Boolean) as Notification[]
  return { list: raw, total: res?.total ?? raw.length }
}

/** 未读总数（STU，驱动红点） */
export async function getUnreadCount(): Promise<number> {
  try {
    const res = await get<{ count?: number }>('/my/notifications/unread-count')
    return Number(res?.count ?? 0)
  } catch {
    return 0
  }
}

/** 单条已读（STU，归属校验） */
export async function readNotification(id: number): Promise<void> {
  await put<void>(`/my/notifications/${id}/read`)
}

/**
 * 全部已读（STU，PUT /my/notifications/read-all；需登录、幂等）。
 * 返回 data = 本次置为已读的条数（无未读时为 0），非分页结构，故不经 recordsOf。
 * 失败向上抛错，由调用方提示且不改变本地状态。
 */
export async function readAllNotifications(): Promise<number> {
  const res = await put<number | { count?: number }>('/my/notifications/read-all')
  const raw = res as { count?: number } | number | null | undefined
  if (typeof raw === 'number') return raw
  return Number(raw?.count ?? 0)
}


