/**
 * 消息通知接口模块（task-09，ARCH §3.4，STU）
 *
 * GET /my/notifications         我的消息（倒序，isRead 过滤）
 * GET /my/notifications/unread-count 未读总数（红点）
 * PUT /my/notifications/{id}/read  单条已读
 * PUT /my/notifications/read-all   全部已读（幂等，需登录）
 */
import { get, put } from './http'
import { DEFAULT_PAGE_SIZE } from '@/constants/paging'
import {
  recordsOf, type PageResult,
  type NotificationVO, type UnreadCountVO,
} from './shared'

/**
 * 通知行（`NotificationVO`，**5 字段**：id / title / content / isRead / createdAt）。
 *
 * 契约不含 `type` / `relatedId`：端上通知卡只渲染「标题 + 正文 + 时间 + 未读态」，
 * **从不按类型分支、不做类型相关跳转**，故两个字段按「零消费即删」不出参。
 * 将来要做「按类型跳转」，须先由 UI 文档定义交互再扩字段。
 */
export interface Notification {
  id: number
  title: string
  content: string
  /** 是否已读：`true`=已读 / `false`=未读（**布尔契约**；由 0/1 数字改） */
  isRead: boolean
  createdAt?: string
}

/** 强类型入参：取自生成契约，后端改字段即编译期报错 */
function toNotification(raw: NotificationVO): Notification | null {
  if (!raw) return null
  return {
    id: Number(raw.id),
    title: raw.title || '',
    content: raw.content || '',
    // 契约已是布尔；保留 0/1 兼容（历史数据），不改变已读判定语义
    isRead: raw.isRead === true || (raw.isRead as unknown) === 1,
    createdAt: raw.createdAt,
  }
}

/**
 * 我的消息列表（STU，倒序）。
 * 分页壳只有 `records`：结束判据 = 本页返回条数 < 请求的 `pageSize`。
 */
export async function listNotifications(params: {
  /** 已读过滤：`true`=仅已读 / `false`=仅未读；不传 = 全部（端上当前恒不传） */
  isRead?: boolean
  page?: number
  pageSize?: number
}): Promise<{ list: Notification[] }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? DEFAULT_PAGE_SIZE,
  }
  if (params.isRead != null) query.isRead = params.isRead
  const res = await get<PageResult<NotificationVO>>('/my/notifications', query)
  return { list: recordsOf<NotificationVO>(res).map(toNotification).filter(Boolean) as Notification[] }
}

/** 未读总数（STU，驱动红点） */
export async function getUnreadCount(): Promise<number> {
  try {
    const res = await get<UnreadCountVO>('/my/notifications/unread-count')
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
