import type { Dish, DishDetail, DishQuery } from '@/types/dish'
import { get } from './http'

const TAG_MAP: Record<string, string> = {
  recommended: '必吃推荐',
  signature: '招牌菜',
  daily: '日常',
  halal: '清真',
  noodle: '面食',
  spicy: '辣味',
  vegetarian: '素食',
  western: '西餐',
}

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

export function toDish(raw: any): Dish {
  const images = normalizeImages(raw.images ?? raw.image)
  const tags = Array.isArray(raw.tags)
    ? raw.tags
    : String(raw.tags || '').split(',').map(item => item.trim()).filter(Boolean)

  return {
    id: Number(raw.id),
    name: raw.name || '',
    price: raw.price ? Math.round(Number(raw.price) / 100) : 0,
    image: images[0] || '',
    images,
    rating: raw.avgRating ?? raw.rating ?? 0,
    ratingCount: raw.ratingCount ?? raw.rating_count ?? 0,
    favoriteCount: raw.collectCount ?? raw.favoriteCount ?? raw.favorite_count ?? 0,
    tags: tags.map((t: string) => TAG_MAP[t] || t),
    description: raw.description || '',
    canteen: raw.canteenName || raw.canteen || '',
    stallName: raw.stallName || '',
  }
}

function toDishDetail(raw: any): DishDetail {
  return {
    ...toDish(raw),
    ratingDistribution: raw.ratingDistribution || [],
  }
}

export async function getRecommendList(): Promise<Dish[]> {
  return recordsOf<any>(await get('/dishes/hot')).map(toDish)
}

export async function getStallDishes(_canteen: string, stallName: string): Promise<Dish[]> {
  const res = await get<any>('/dishes', { keyword: stallName, page: 1, pageSize: 50 })
  return recordsOf<any>(res).map(toDish)
}

export async function searchDishes(query: DishQuery): Promise<Dish[]> {
  const params: Record<string, any> = { page: 1, pageSize: 50 }
  if (query.keyword) params.keyword = query.keyword
  if (query.minPrice !== undefined) params.minPrice = Math.round(query.minPrice * 100)
  if (query.maxPrice !== undefined) params.maxPrice = Math.round(query.maxPrice * 100)
  if (query.sortBy === 'rating') params.sortBy = 'rating'
  if (query.sortBy === 'favoriteCount') params.sortBy = 'favoriteCount'
  if (query.sortBy === 'price') params.sortBy = 'price'

  const res = await get<any>('/dishes', params)
  return recordsOf<any>(res).map(toDish)
}

export async function getDishDetail(id: number): Promise<DishDetail> {
  const raw = await get<any>(`/dishes/${id}`)
  return toDishDetail(raw)
}
