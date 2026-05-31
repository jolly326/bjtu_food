import type { Canteen } from '@/types'

let _id = 1000
function nextId(): bigint { return ++_id as unknown as bigint }

function findIdx(arr: Canteen[], id: number) { return arr.findIndex(c => Number(c.id) === id) }

const mockData: Canteen[] = [
  { id: 1 as unknown as bigint, name: '学苑食堂', image: '', location: '主校区东侧', description: '两层大型食堂，品种丰富', sort_order: 1, status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
  { id: 2 as unknown as bigint, name: '明湖食堂', image: '', location: '主校区西侧', description: '靠近图书馆，环境优雅', sort_order: 2, status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
  { id: 3 as unknown as bigint, name: '留园食堂', image: '', location: '东校区', description: '留学生餐厅，口味多样', sort_order: 3, status: 'inactive', created_at: new Date('2024-02-15'), updated_at: new Date('2024-02-15') },
]

export function getAll() { return mockData }
export function create(data: Omit<Canteen, 'id' | 'created_at' | 'updated_at'>) {
  const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Canteen
  mockData.push(item); return item
}
export function updateById(id: number, data: Partial<Canteen>) {
  const idx = findIdx(mockData, id)
  if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
}
export function deleteById(id: number) {
  const idx = findIdx(mockData, id)
  if (idx !== -1) mockData.splice(idx, 1)
}
