import type { Stall } from '@/types'
import { findIdx, nextId } from './common'
import { del, get, post, put } from './http'
import { stallToApi, stallToLegacy } from './adapter'

const mockData: Stall[] = [
  { id: 1 as unknown as bigint, canteen_id: 1 as unknown as bigint, name: '麻辣香锅', image: '', location: '', description: '自选麻辣香锅', avg_rating: 4.5, sort_order: 1, status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
]

export async function getAll(): Promise<Stall[]> {
  try {
    return (await get<any[]>('/admin/stalls')).map(stallToLegacy)
  } catch {
    console.log('[stall] 降级到 Mock')
    return [...mockData]
  }
}

export async function create(data: Omit<Stall, 'id' | 'created_at' | 'updated_at'>) {
  try {
    return await post<Stall>('/admin/stalls', stallToApi(data))
  } catch {
    console.log('[stall] create 降级到 Mock')
    const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Stall
    mockData.push(item)
    return item
  }
}

export async function updateById(id: number, data: Partial<Stall>) {
  try {
    return await put<Stall>(`/admin/stalls/${id}`, stallToApi(data))
  } catch {
    console.log('[stall] update 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
  }
}

export async function deleteById(id: number) {
  try {
    return await del(`/admin/stalls/${id}`)
  } catch {
    console.log('[stall] delete 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) mockData.splice(idx, 1)
  }
}
