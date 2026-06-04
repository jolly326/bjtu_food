import type { Review } from '@/types'
import { nextId, findIdx } from './common'
import { get, post, put, del } from './http'

const mockData: Review[] = [
  { id: 1 as unknown as bigint, user_id: 1 as unknown as bigint, dish_id: 3 as unknown as bigint, rating: 5, content: '味道正宗，牛肉很大块！', images: '', is_hidden: 0, created_at: new Date('2024-03-01'), updated_at: new Date('2024-03-01') },
  { id: 2 as unknown as bigint, user_id: 2 as unknown as bigint, dish_id: 7 as unknown as bigint, rating: 4, content: '性价比很高，就是排队太久了', images: '', is_hidden: 0, created_at: new Date('2024-03-05'), updated_at: new Date('2024-03-05') },
  { id: 3 as unknown as bigint, user_id: 3 as unknown as bigint, dish_id: 5 as unknown as bigint, rating: 3, content: '一般般，没有以前好吃了', images: '', is_hidden: 0, created_at: new Date('2024-03-10'), updated_at: new Date('2024-03-10') },
  { id: 4 as unknown as bigint, user_id: 1 as unknown as bigint, dish_id: 1 as unknown as bigint, rating: 5, content: '每次必点，强烈推荐！', images: '', is_hidden: 0, created_at: new Date('2024-03-15'), updated_at: new Date('2024-03-15') },
  { id: 5 as unknown as bigint, user_id: 4 as unknown as bigint, dish_id: 10 as unknown as bigint, rating: 4, content: '汤底浓郁，不错', images: '', is_hidden: 0, created_at: new Date('2024-03-20'), updated_at: new Date('2024-03-20') },
  { id: 6 as unknown as bigint, user_id: 5 as unknown as bigint, dish_id: 4 as unknown as bigint, rating: 2, content: '太咸了，希望能改进', images: '', is_hidden: 0, created_at: new Date('2024-04-01'), updated_at: new Date('2024-04-01') },
]

export async function getAll(): Promise<Review[]> {
  try { return await get<Review[]>('/reviews') }
  catch { console.log('[review] 降级到 Mock'); return [...mockData] }
}
export async function create(data: Omit<Review, 'id' | 'created_at' | 'updated_at'>) {
  try { return await post<Review>('/reviews', data) }
  catch { console.log('[review] create 降级到 Mock'); const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Review; mockData.push(item); return item }
}
export async function updateById(id: number, data: Partial<Review>) {
  try { return await put<Review>(`/reviews/${id}`, data) }
  catch { console.log('[review] update 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() }) }
}
export async function deleteById(id: number) {
  try { return await del(`/reviews/${id}`) }
  catch { console.log('[review] delete 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) mockData.splice(idx, 1) }
}
