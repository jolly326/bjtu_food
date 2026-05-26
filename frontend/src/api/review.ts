import { request } from './request'
import type { Review, ReviewSubmit } from '@/types/review'

export function getReviewsByDish(dishId: number): Promise<Review[]> {
  // MVP mock
  return Promise.resolve([
    { id: 1, userId: 1, userName: '交大干饭人', userAvatar: '', dishId, rating: 5, content: '超级好吃！每次必点！', images: [], createTime: '2024-12-20' },
    { id: 2, userId: 2, userName: '食堂探索家', userAvatar: '', dishId, rating: 4, content: '味道不错，分量也足', images: [], createTime: '2024-12-18' },
    { id: 3, userId: 3, userName: '美食猎人', userAvatar: '', dishId, rating: 5, content: '强烈推荐，交大最好吃的！', images: [], createTime: '2024-12-15' },
  ])
}

export function submitReview(data: ReviewSubmit): Promise<void> {
  return Promise.resolve()
}
