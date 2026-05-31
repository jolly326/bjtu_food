import type { Stall } from '@/types'

let _id = 1000
function nextId(): bigint { return ++_id as unknown as bigint }
function findIdx(arr: Stall[], id: number) { return arr.findIndex(s => Number(s.id) === id) }

const mockData: Stall[] = [
  { id: 1 as unknown as bigint, canteen_id: 1 as unknown as bigint, name: '麻辣香锅', image: '', location: '', description: '自选麻辣香锅', avg_rating: 4.5, sort_order: 1, status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
  { id: 2 as unknown as bigint, canteen_id: 1 as unknown as bigint, name: '兰州拉面', image: '', location: '', description: '正宗兰州牛肉拉面', avg_rating: 4.8, sort_order: 2, status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
  { id: 3 as unknown as bigint, canteen_id: 1 as unknown as bigint, name: '煎饼果子', image: '', location: '', description: '天津煎饼果子', avg_rating: 4.2, sort_order: 3, status: 'active', created_at: new Date('2024-01-15'), updated_at: new Date('2024-01-15') },
  { id: 4 as unknown as bigint, canteen_id: 2 as unknown as bigint, name: '黄焖鸡米饭', image: '', location: '', description: '招牌黄焖鸡', avg_rating: 4.6, sort_order: 1, status: 'active', created_at: new Date('2024-02-01'), updated_at: new Date('2024-02-01') },
  { id: 5 as unknown as bigint, canteen_id: 2 as unknown as bigint, name: '桂林米粉', image: '', location: '', description: '桂林特色米粉', avg_rating: 4.0, sort_order: 2, status: 'inactive', created_at: new Date('2024-02-01'), updated_at: new Date('2024-02-01') },
  { id: 6 as unknown as bigint, canteen_id: 3 as unknown as bigint, name: '日式拉面', image: '', location: '', description: '日式豚骨拉面', avg_rating: 4.3, sort_order: 1, status: 'active', created_at: new Date('2024-02-20'), updated_at: new Date('2024-02-20') },
]

export function getAll() { return mockData }
export function create(data: Omit<Stall, 'id' | 'created_at' | 'updated_at'>) {
  const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Stall
  mockData.push(item); return item
}
export function updateById(id: number, data: Partial<Stall>) {
  const idx = findIdx(mockData, id)
  if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
}
export function deleteById(id: number) {
  const idx = findIdx(mockData, id)
  if (idx !== -1) mockData.splice(idx, 1)
}
