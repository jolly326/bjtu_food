/**
 * 消息通知接口模块（task-09，ARCH §3.4，STU）
 *
 * GET /my/notifications         我的消息（倒序，isRead 过滤）
 * GET /my/notifications/unread-count 未读总数（红点）
 * PUT /my/notifications/{id}/read  单条已读
 * PUT /my/notifications/read-all    全部已读
 */
import { get, put } from './http'

export type NotificationType = 'moment_audit' | 'dish_audit' | 'comment' | 'useful'

export interface Notification {
  id: number
  /** 通知类型 */
  type: NotificationType
  title: string
  content: string
  /** 关联对象 ID（按 type 解释：动态/菜品 ID） */
  relatedId?: number | null
  /** 是否已读：0=未读 1=已读 */
  isRead: number
  createdAt?: string
}

interface PageResult<T> {
  list?: T[]
  records?: T[]
  total?: number
  page?: number
  pageSize?: number
}

function listOf<T>(res: PageResult<T> | undefined): T[] {
  if (!res) return []
  return res.list || res.records || []
}

function toNotification(raw: any): Notification | null {
  if (!raw) return null
  return {
    id: Number(raw.id),
    type: (raw.type as NotificationType) || 'moment_audit',
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
  const query: Record<string, any> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.isRead != null) query.isRead = params.isRead
  const res = await get<PageResult<any>>('/my/notifications', query)
  const raw = listOf(res).map(toNotification).filter(Boolean) as Notification[]
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

// ─────────────────────────────────────────────────────────────
// 首页广播条类型：数据源为「动态前 10 条」（见 pages/home/index.vue toBroadcastItem）。
// 原 getBroadcasts()（broadcast 表接口）已随广播条改版下线。
// ─────────────────────────────────────────────────────────────

export interface BroadcastItem {
  text: string
  type: 'dish' | 'community' | 'url' | 'canteen' | 'stall'
  targetId?: number
  targetUrl?: string
}
