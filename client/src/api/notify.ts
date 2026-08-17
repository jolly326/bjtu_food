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

/** 全部已读（STU） */
export async function readAllNotifications(): Promise<void> {
  await put<void>('/my/notifications/read-all')
}

// ─────────────────────────────────────────────────────────────
// 首页广播条（原 api/broadcast.ts 并入本模块：规避「新增顶层模块文件
// 未被微信开发者工具注册」问题，产物不再生成 api/broadcast.js）
// ─────────────────────────────────────────────────────────────

/** 广播类型（后端契约 A.14：BroadcastVO.broadcastType） */
type BroadcastType = 'NOTICE' | 'ACTIVITY' | 'DISH' | 'URL' | 'NONE'

interface BroadcastVO {
  id: number
  title: string
  content: string
  broadcastType: BroadcastType
  targetId?: number
  targetUrl?: string
  createdAt?: string
}

/** 首页广播条分发类型（前端 UI 语义，对应 home BroadcastItem.type） */
type BroadcastDispatch = 'dish' | 'community' | 'url' | 'canteen' | 'stall'

export interface BroadcastItem {
  text: string
  type: BroadcastDispatch
  targetId?: number
  targetUrl?: string
}

function mapBroadcastType(t: BroadcastType): BroadcastDispatch {
  switch (t) {
    case 'DISH': return 'dish'
    case 'URL': return 'url'
    // NOTICE / ACTIVITY / NONE 及未知类型：回落社区流（与历史默认公告行为一致）
    default: return 'community'
  }
}

/** GET /broadcasts（公开）→ 首页广播条数据源；无数据返回空数组，UI 保留轻量占位不隐藏 */
export async function getBroadcasts(): Promise<BroadcastItem[]> {
  const list = await get<BroadcastVO[]>('/broadcasts')
  if (!Array.isArray(list)) return []
  return list.map(b => ({
    text: b.title || b.content || '',
    type: mapBroadcastType(b.broadcastType),
    targetId: b.targetId,
    targetUrl: b.targetUrl,
  }))
}
