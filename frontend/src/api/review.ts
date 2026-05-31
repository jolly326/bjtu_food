import type { Review, ReviewSubmit } from '@/types/review'
import { get, post } from './http'

const MOCK_REVIEWS: Review[] = [
  { id: 1, userId: 1, userName: '交大干饭人', userAvatar: '', dishId: 0, rating: 5, content: '超级好吃！每次必点！', images: [], createTime: '2024-12-20' },
  { id: 2, userId: 2, userName: '美食猎人', userAvatar: '', dishId: 0, rating: 4, content: '味道不错，分量也足', images: [], createTime: '2024-12-18' },
]

function toReview(raw: any): Review {
  return {
    id: raw.id, userId: raw.userId, userName: raw.userNickname || '匿名用户',
    userAvatar: raw.userAvatar || '', dishId: raw.dishId || 0,
    rating: raw.rating, content: raw.content || '',
    images: raw.images || [], createTime: raw.createdAt || '',
  }
}

export async function getReviewsByDish(dishId: number): Promise<Review[]> {
  try {
    const res: any = await get(`/dishes/${dishId}/reviews`, { page: 1, pageSize: 20 })
    const list = res.records || res || []
    return list.map(toReview)
  } catch {
    console.log('[review] getReviewsByDish 失败，使用 mock')
    return MOCK_REVIEWS.map(r => ({ ...r, dishId }))
  }
}

export async function submitReview(data: ReviewSubmit): Promise<void> {
  try {
    await post('/reviews', { dishId: data.dishId, rating: data.rating, content: data.content, images: data.images })
  } catch {
    console.log('[review] submitReview 失败')
    throw new Error('提交评价失败')
  }
}
