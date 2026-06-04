import type { Banner } from '@/types'

let _id = 100
function nextId(): bigint { return ++_id as unknown as bigint }

function findIdx(arr: Banner[], id: number) { return arr.findIndex(b => Number(b.id) === id) }

const mockData: Banner[] = [
  { id: 1 as unknown as bigint, title: '开学季优惠', image: '', type: 'carousel', sort_order: 1, status: 'active', created_at: new Date('2024-09-01'), updated_at: new Date('2024-09-01') },
  { id: 2 as unknown as bigint, title: '新生食堂指南', image: '', type: 'carousel', sort_order: 2, status: 'active', created_at: new Date('2024-09-05'), updated_at: new Date('2024-09-05') },
  { id: 3 as unknown as bigint, title: '冬季新菜品鉴', image: '', type: 'carousel', sort_order: 3, status: 'inactive', created_at: new Date('2024-11-01'), updated_at: new Date('2024-11-01') },
]

export function getAll() { return mockData }
export function create(data: Omit<Banner, 'id' | 'created_at' | 'updated_at'>) {
  const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Banner
  mockData.push(item); return item
}
export function updateById(id: number, data: Partial<Banner>) {
  const idx = findIdx(mockData, id)
  if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
}
export function deleteById(id: number) {
  const idx = findIdx(mockData, id)
  if (idx !== -1) mockData.splice(idx, 1)
}
