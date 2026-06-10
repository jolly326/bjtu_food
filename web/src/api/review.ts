import type { Review } from '@/types'
import { del, get, post, put } from './http'
import { pageRecords, reviewToLegacy } from './adapter'

export async function getAll(): Promise<Review[]> {
  return pageRecords(await get<any>('/admin/reviews', { page: 1, pageSize: 200 })).map(reviewToLegacy)
}

export async function create(data: Omit<Review, 'id' | 'created_at' | 'updated_at'>) {
  return await post<Review>('/reviews', data)
}

export async function updateById(id: number, _data: Partial<Review>) {
  await put<void>(`/admin/reviews/${id}/hide`)
}

export async function deleteById(id: number) {
  await del<void>(`/admin/reviews/${id}`)
}
