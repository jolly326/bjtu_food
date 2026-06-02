import type { Review } from '@/types'

function findIdx(arr: Review[], id: number) { return arr.findIndex(r => Number(r.id) === id) }

const mockData: Review[] = [
  { id: 1 as unknown as bigint, user_id: 1 as unknown as bigint, dish_id: 3 as unknown as bigint, rating: 5, content: '味道正宗，牛肉很大块！', images: '', is_hidden: 0, created_at: new Date('2024-03-01'), updated_at: new Date('2024-03-01') },
  { id: 2 as unknown as bigint, user_id: 2 as unknown as bigint, dish_id: 7 as unknown as bigint, rating: 4, content: '性价比很高，就是排队太久了', images: '', is_hidden: 0, created_at: new Date('2024-03-05'), updated_at: new Date('2024-03-05') },
  { id: 3 as unknown as bigint, user_id: 3 as unknown as bigint, dish_id: 5 as unknown as bigint, rating: 3, content: '一般般，没有以前好吃了', images: '', is_hidden: 0, created_at: new Date('2024-03-10'), updated_at: new Date('2024-03-10') },
  { id: 4 as unknown as bigint, user_id: 1 as unknown as bigint, dish_id: 1 as unknown as bigint, rating: 5, content: '每次必点，强烈推荐！', images: '', is_hidden: 0, created_at: new Date('2024-03-15'), updated_at: new Date('2024-03-15') },
  { id: 5 as unknown as bigint, user_id: 4 as unknown as bigint, dish_id: 10 as unknown as bigint, rating: 4, content: '汤底浓郁，不错', images: '', is_hidden: 0, created_at: new Date('2024-03-20'), updated_at: new Date('2024-03-20') },
  { id: 6 as unknown as bigint, user_id: 5 as unknown as bigint, dish_id: 4 as unknown as bigint, rating: 2, content: '太咸了，希望能改进', images: '', is_hidden: 0, created_at: new Date('2024-04-01'), updated_at: new Date('2024-04-01') },
]

export function getAll() { return mockData }
export function deleteById(id: number) {
  const idx = findIdx(mockData, id)
  if (idx !== -1) mockData.splice(idx, 1)
}
