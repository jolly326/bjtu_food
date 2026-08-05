import type { Review } from '@/types'
import { del, get, put } from './http'
import { pageRecords, reviewToLegacy } from './adapter'

export async function getAll(userId?: number): Promise<Review[]> {
  const query: Record<string, unknown> = { page: 1, pageSize: 200 }
  if (userId != null) query.userId = userId
  return pageRecords(await get<any>('/admin/reviews', query)).map(reviewToLegacy)
}

// 注意：评价由学生端提交（POST /reviews），后台仅审核 hide / delete，不提供 create。
export async function updateById(id: number, _data: Partial<Review>) {
  await put<void>(`/admin/reviews/${id}/hide`)
}

export async function deleteById(id: number) {
  await del<void>(`/admin/reviews/${id}`)
}
