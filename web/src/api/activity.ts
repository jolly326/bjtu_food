import { get, post, put, del } from './http'

/**
 * 活动管理（最新活动/公众号文章卡片）。
 * GET/POST /admin/activities、PUT/DELETE /admin/activities/{id}
 */
export interface Activity {
  id: number
  title: string
  description?: string
  image?: string
  articleUrl?: string
  status?: string
  sortOrder?: number
  createdAt?: string
}

export interface ActivityPage {
  list: Activity[]
  total: number
}

export async function listActivities(params: {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
} = {}): Promise<ActivityPage> {
  const res = await get<any>('/admin/activities', params)
  const list = res?.list ?? res?.records ?? []
  return { list: list.map((r: any) => ({
    id: Number(r.id),
    title: r.title || '',
    description: r.description || '',
    image: r.image || '',
    articleUrl: r.articleUrl || '',
    status: r.status || 'enabled',
    sortOrder: r.sortOrder ?? 0,
    createdAt: r.createdAt || '',
  })), total: Number(res?.total ?? list.length) }
}

export async function createActivity(data: Partial<Activity>) {
  return await post('/admin/activities', data)
}

export async function updateActivity(id: number, data: Partial<Activity>) {
  return await put(`/admin/activities/${id}`, data)
}

export async function deleteActivity(id: number) {
  return await del(`/admin/activities/${id}`)
}
