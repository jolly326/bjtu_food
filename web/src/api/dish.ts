import type { Dish } from '@/types'
import { nextId, findIdx } from './common'
import { get, post, put, del } from './http'

const mockData: Dish[] = [
  { id: 1 as unknown as bigint, stall_id: 1 as unknown as bigint, name: '微辣香锅', image: '', price: 28, tags: '["招牌菜"]', description: '微辣口味，配料丰富', avg_rating: 4.5, rating_count: 30, favorite_count: 15, view_count: 200, status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
  { id: 2 as unknown as bigint, stall_id: 1 as unknown as bigint, name: '中辣香锅', image: '', price: 32, tags: '', description: '中辣口味，麻辣鲜香', avg_rating: 4.3, rating_count: 20, favorite_count: 10, view_count: 150, status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
  { id: 3 as unknown as bigint, stall_id: 2 as unknown as bigint, name: '牛肉拉面', image: '', price: 15, tags: '["招牌菜","必吃推荐"]', description: '正宗兰州牛肉拉面，汤清味浓', avg_rating: 4.8, rating_count: 50, favorite_count: 40, view_count: 500, status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
  { id: 4 as unknown as bigint, stall_id: 2 as unknown as bigint, name: '拌面', image: '', price: 13, tags: '', description: '劲道拌面，酱香浓郁', avg_rating: 4.0, rating_count: 15, favorite_count: 5, view_count: 80, status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
  { id: 5 as unknown as bigint, stall_id: 3 as unknown as bigint, name: '经典煎饼', image: '', price: 8, tags: '["必吃推荐"]', description: '天津煎饼果子，外酥里嫩', avg_rating: 4.2, rating_count: 25, favorite_count: 20, view_count: 300, status: 'active', created_at: new Date('2024-01-15'), updated_at: new Date('2024-01-15') },
  { id: 6 as unknown as bigint, stall_id: 3 as unknown as bigint, name: '加蛋煎饼', image: '', price: 10, tags: '', description: '经典煎饼加蛋，营养更丰富', avg_rating: 4.1, rating_count: 18, favorite_count: 8, view_count: 120, status: 'active', created_at: new Date('2024-01-15'), updated_at: new Date('2024-01-15') },
  { id: 7 as unknown as bigint, stall_id: 4 as unknown as bigint, name: '黄焖鸡套餐', image: '', price: 18, tags: '["招牌菜"]', description: '招牌黄焖鸡，配米饭和汤', avg_rating: 4.6, rating_count: 35, favorite_count: 25, view_count: 400, status: 'active', created_at: new Date('2024-02-01'), updated_at: new Date('2024-02-01') },
  { id: 8 as unknown as bigint, stall_id: 4 as unknown as bigint, name: '黄焖排骨套餐', image: '', price: 22, tags: '', description: '排骨炖得软烂，汤汁浓郁', avg_rating: 4.4, rating_count: 22, favorite_count: 12, view_count: 180, status: 'active', created_at: new Date('2024-02-01'), updated_at: new Date('2024-02-01') },
  { id: 9 as unknown as bigint, stall_id: 5 as unknown as bigint, name: '卤菜粉', image: '', price: 12, tags: '', description: '桂林特色卤菜粉', avg_rating: 3.8, rating_count: 10, favorite_count: 3, view_count: 60, status: 'inactive', created_at: new Date('2024-02-01'), updated_at: new Date('2024-02-01') },
  { id: 10 as unknown as bigint, stall_id: 6 as unknown as bigint, name: '豚骨拉面', image: '', price: 25, tags: '["必吃推荐"]', description: '日式豚骨拉面，汤底浓郁', avg_rating: 4.7, rating_count: 40, favorite_count: 30, view_count: 350, status: 'active', created_at: new Date('2024-02-20'), updated_at: new Date('2024-02-20') },
]

export async function getAll(): Promise<Dish[]> {
  try { return await get<Dish[]>('/dishes') }
  catch { console.log('[dish] 降级到 Mock'); return [...mockData] }
}
export async function create(data: Omit<Dish, 'id' | 'created_at' | 'updated_at'>) {
  try { return await post<Dish>('/dishes', data) }
  catch { console.log('[dish] create 降级到 Mock'); const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Dish; mockData.push(item); return item }
}
export async function updateById(id: number, data: Partial<Dish>) {
  try { return await put<Dish>(`/dishes/${id}`, data) }
  catch { console.log('[dish] update 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() }) }
}
export async function deleteById(id: number) {
  try { return await del(`/dishes/${id}`) }
  catch { console.log('[dish] delete 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) mockData.splice(idx, 1) }
}
