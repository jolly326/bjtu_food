import type { Review } from '@/types'
import { findIdx, nextId } from './common'
import { del, get, post, put } from './http'
import { pageRecords, reviewToLegacy } from './adapter'

const mockData: Review[] = [
  { id: 1 as unknown as bigint, user_id: 1 as unknown as bigint, dish_id: 3 as unknown as bigint, rating: 5, content: '味道正宗，牛肉很大块。', images: '', is_hidden: 0, created_at: new Date('2024-03-01'), updated_at: new Date('2024-03-01') },
]

export async function getAll(): Promise<Review[]> {
  try {
    return pageRecords(await get<any>('/admin/reviews', { page: 1, pageSize: 200 })).map(reviewToLegacy)
  } catch {
    console.log('[review] 降级到 Mock')
    return [...mockData]
  }
}

export async function create(data: Omit<Review, 'id' | 'created_at' | 'updated_at'>) {
  try {
    return await post<Review>('/reviews', data)
  } catch {
    console.log('[review] create 降级到 Mock')
    const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Review
    mockData.push(item)
    return item
  }
}

export async function updateById(id: number, data: Partial<Review>) {
  try {
    return await put<Review>(`/admin/reviews/${id}/hide`, data)
  } catch {
    console.log('[review] update 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
  }
}

export async function deleteById(id: number) {
  try {
    return await del(`/admin/reviews/${id}`)
  } catch {
    console.log('[review] delete 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) mockData.splice(idx, 1)
  }
}
