import type { Canteen } from '@/types'
import { findIdx, nextId } from './common'
import { del, get, post, put } from './http'
import { canteenToApi, canteenToLegacy } from './adapter'

const mockData: Canteen[] = [
  { id: 1 as unknown as bigint, name: '学苑食堂', image: '', location: '主校区东侧', description: '两层大型食堂，品种丰富', sort_order: 1, status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
]

export async function getAll(): Promise<Canteen[]> {
  try {
    return (await get<any[]>('/admin/canteens')).map(canteenToLegacy)
  } catch {
    console.log('[canteen] 降级到 Mock')
    return [...mockData]
  }
}

export async function create(data: Omit<Canteen, 'id' | 'created_at' | 'updated_at'>) {
  try {
    return await post<Canteen>('/admin/canteens', canteenToApi(data))
  } catch {
    console.log('[canteen] create 降级到 Mock')
    const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Canteen
    mockData.push(item)
    return item
  }
}

export async function updateById(id: number, data: Partial<Canteen>) {
  try {
    return await put<Canteen>(`/admin/canteens/${id}`, canteenToApi(data))
  } catch {
    console.log('[canteen] update 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
  }
}

export async function deleteById(id: number) {
  try {
    return await del(`/admin/canteens/${id}`)
  } catch {
    console.log('[canteen] delete 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) mockData.splice(idx, 1)
  }
}
