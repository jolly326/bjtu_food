/**
 * 消息通知接口模块（task-09，ARCH §3.4，STU）
 *
 * GET /my/notifications         我的消息（倒序，isRead 过滤）
 * GET /my/notifications/unread-count 未读总数（红点）
 * PUT /my/notifications/{id}/read  单条已读
 * PUT /my/notifications/read-all   全部已读（幂等，需登录）
 */
import { get, put } from './http'
import { recordsOf, type PageResult, type RawRow } from './shared'

/**
 * 通知类型（原值透传，不做字面量收窄）：后端现产生 feedback_handle（反馈处理结果回执）与
 * correction_handle（菜品信息纠错回执）；端上对未知类型容错（不跳转、不崩溃）。
 */
type NotificationType = string

export interface Notification {
  id: number
  /** 通知类型：feedback_handle=反馈处理结果回执 / correction_handle=菜品信息纠错回执；其他值＝未知类型（端上容错） */
  type: NotificationType
  title: string
  content: string
  /** 是否已读：0=未读 1=已读 */
  isRead: number
  createdAt?: string
}

function toNotification(raw: RawRow): Notification | null {
  if (!raw) return null
  return {
    id: Number(raw.id),
    // 缺省/未知类型一律原值透传（缺失时为空串），端上按未知类型容错。
    type: (raw.type as NotificationType) || '',
    title: raw.title || '',
    content: raw.content || '',
    isRead: Number(raw.isRead ?? 0),
    createdAt: raw.createdAt,
  }
}

/**
 * 我的消息列表（STU，倒序）。
 * 分页壳只有 `records`：结束判据 = 本页返回条数 < 请求的 `pageSize`。
 */
export async function getNotifications(params: {
  isRead?: 0 | 1
  page?: number
  pageSize?: number
}): Promise<{ list: Notification[] }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.isRead != null) query.isRead = params.isRead
  const res = await get<PageResult<RawRow>>('/my/notifications', query)
  return { list: recordsOf(res).map(toNotification).filter(Boolean) as Notification[] }
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

/** 单条已读（STU，归属校验；data = null） */
export async function readNotification(id: number): Promise<void> {
  await put<void>(`/my/notifications/${id}/read`)
}

/**
 * 全部已读（STU，PUT /my/notifications/read-all；需登录、幂等）。
 * `data` = `null`（无载荷，成功即 code=200）；失败向上抛错，由调用方提示且不改变本地状态。
 */
export async function readAllNotifications(): Promise<void> {
  await put<void>('/my/notifications/read-all')
}
