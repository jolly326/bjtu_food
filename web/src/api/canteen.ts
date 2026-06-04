import type { Canteen } from '@/types'
import { nextId, findIdx } from './common'
import { get, post, put, del } from './http'

const mockData: Canteen[] = [
  { id: 1 as unknown as bigint, name: '学苑食堂', image: '', location: '主校区东侧', description: '两层大型食堂，品种丰富', sort_order: 1, status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
  { id: 2 as unknown as bigint, name: '明湖食堂', image: '', location: '主校区西侧', description: '靠近图书馆，环境优雅', sort_order: 2, status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
  { id: 3 as unknown as bigint, name: '留园食堂', image: '', location: '东校区', description: '留学生餐厅，口味多样', sort_order: 3, status: 'inactive', created_at: new Date('2024-02-15'), updated_at: new Date('2024-02-15') },
]

export async function getAll(): Promise<Canteen[]> {
  try { return await get<Canteen[]>('/canteens') }
  catch { console.log('[canteen] 降级到 Mock'); return [...mockData] }
}
export async function create(data: Omit<Canteen, 'id' | 'created_at' | 'updated_at'>) {
  try { return await post<Canteen>('/canteens', data) }
  catch { console.log('[canteen] create 降级到 Mock'); const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Canteen; mockData.push(item); return item }
}
export async function updateById(id: number, data: Partial<Canteen>) {
  try { return await put<Canteen>(`/canteens/${id}`, data) }
  catch { console.log('[canteen] update 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() }) }
}
export async function deleteById(id: number) {
  try { return await del(`/canteens/${id}`) }
  catch { console.log('[canteen] delete 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) mockData.splice(idx, 1) }
}
