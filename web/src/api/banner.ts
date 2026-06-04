import type { Banner } from '@/types'
import { nextId, findIdx } from './common'
import { get, post, put, del } from './http'

const mockData: Banner[] = [
  { id: 1 as unknown as bigint, title: '开学季优惠', image: '', type: 'carousel', sort_order: 1, status: 'active', created_at: new Date('2024-09-01'), updated_at: new Date('2024-09-01') },
  { id: 2 as unknown as bigint, title: '新生食堂指南', image: '', type: 'carousel', sort_order: 2, status: 'active', created_at: new Date('2024-09-05'), updated_at: new Date('2024-09-05') },
  { id: 3 as unknown as bigint, title: '冬季新菜品鉴', image: '', type: 'carousel', sort_order: 3, status: 'inactive', created_at: new Date('2024-11-01'), updated_at: new Date('2024-11-01') },
]

export async function getAll(): Promise<Banner[]> {
  try { return await get<Banner[]>('/banners') }
  catch { console.log('[banner] 降级到 Mock'); return [...mockData] }
}
export async function create(data: Omit<Banner, 'id' | 'created_at' | 'updated_at'>) {
  try { return await post<Banner>('/banners', data) }
  catch { console.log('[banner] create 降级到 Mock'); const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Banner; mockData.push(item); return item }
}
export async function updateById(id: number, data: Partial<Banner>) {
  try { return await put<Banner>(`/banners/${id}`, data) }
  catch { console.log('[banner] update 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() }) }
}
export async function deleteById(id: number) {
  try { return await del(`/banners/${id}`) }
  catch { console.log('[banner] delete 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) mockData.splice(idx, 1) }
}
