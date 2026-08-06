import { get, post, put, del } from './http'

/**
 * 广播管理（首页滚动通知条，task-14 W6）。
 * GET/POST /admin/broadcasts、PUT/DELETE /admin/broadcasts/{id}
 */

export interface Broadcast {
  id: number
  title: string
  content: string
  broadcastType: string
  targetId?: number
  targetUrl?: string
  sortOrder?: number
  status?: string
  createdAt?: string
}

export const BROADCAST_TYPE_LABEL: Record<string, string> = {
  NOTICE: '通知',
  ACTIVITY: '活动',
  DISH: '菜品',
  URL: '外链',
  NONE: '无跳转',
}

export async function listBroadcasts(): Promise<Broadcast[]> {
  return await get<Broadcast[]>('/admin/broadcasts')
}

export async function createBroadcast(data: Partial<Broadcast>) {
  return await post('/admin/broadcasts', data)
}

export async function updateBroadcast(id: number, data: Partial<Broadcast>) {
  return await put(`/admin/broadcasts/${id}`, data)
}

export async function deleteBroadcast(id: number) {
  return await del(`/admin/broadcasts/${id}`)
}
