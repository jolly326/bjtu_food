import type { Dish } from '@/types'
import { findIdx, nextId } from './common'
import { del, get, post, put } from './http'
import { dishToApi, dishToLegacy } from './adapter'

const mockData: Dish[] = [
  { id: 1 as unknown as bigint, stall_id: 1 as unknown as bigint, name: '微辣香锅', image: '', price: 28, tags: 'signature', description: '微辣口味，配料丰富', avg_rating: 4.5, rating_count: 30, favorite_count: 15, view_count: 200, status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
]

export async function getAll(): Promise<Dish[]> {
  try {
    return (await get<any[]>('/admin/dishes')).map(dishToLegacy)
  } catch {
    console.log('[dish] 降级到 Mock')
    return [...mockData]
  }
}

export async function create(data: Omit<Dish, 'id' | 'created_at' | 'updated_at'>) {
  try {
    return await post<Dish>('/admin/dishes', dishToApi(data))
  } catch {
    console.log('[dish] create 降级到 Mock')
    const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Dish
    mockData.push(item)
    return item
  }
}

export async function updateById(id: number, data: Partial<Dish>) {
  try {
    return await put<Dish>(`/admin/dishes/${id}`, dishToApi(data))
  } catch {
    console.log('[dish] update 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
  }
}

export async function deleteById(id: number) {
  try {
    return await del(`/admin/dishes/${id}`)
  } catch {
    console.log('[dish] delete 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) mockData.splice(idx, 1)
  }
}
