import type { Review, ReviewSubmit } from '@/types/review'
import { get, post } from './http'

type PageLike<T> = T[] | { records?: T[]; list?: T[] }

function recordsOf<T>(value: PageLike<T> | undefined | null): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  return value.records || value.list || []
}

function normalizeImages(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string' && item.length > 0)
  if (typeof value !== 'string' || !value.trim()) return []
  const text = value.trim()
  try {
    const parsed = JSON.parse(text)
    return Array.isArray(parsed) ? normalizeImages(parsed) : [text]
  } catch {
    return text.split('|||').map(item => item.trim()).filter(Boolean)
  }
}

function toReview(raw: any): Review {
  return {
    id: Number(raw.id),
    userId: Number(raw.userId ?? raw.user_id ?? 0),
    userName: raw.userNickname || raw.userName || raw.nickname || '匿名用户',
    userAvatar: raw.userAvatar || raw.avatar || '',
    dishId: Number(raw.dishId ?? raw.dish_id ?? 0),
    rating: Number(raw.rating || 0),
    content: raw.content || '',
    images: normalizeImages(raw.images),
    createTime: raw.createdAt || raw.created_at || '',
  }
}

export async function getReviewsByDish(dishId: number): Promise<Review[]> {
  const res = await get<any>(`/dishes/${dishId}/reviews`, { page: 1, pageSize: 20 })
  return recordsOf<any>(res).map(toReview)
}

export async function submitReview(data: ReviewSubmit): Promise<void> {
  await post('/reviews', { dishId: data.dishId, rating: data.rating, content: data.content, images: data.images || [] })
}
