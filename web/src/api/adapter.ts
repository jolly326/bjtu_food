import type { Canteen, Dish, Review, Stall, User } from '@/types'
import { API_BASE_URL } from './config'

type PageLike<T> = T[] | { records?: T[] }

export function pageRecords<T>(data: PageLike<T>): T[] {
  return Array.isArray(data) ? data : data.records || []
}

export function imagesToLegacy(images: unknown): string {
  if (Array.isArray(images)) return images.filter(Boolean).map(toAbsoluteImageUrl).join('|||')
  if (typeof images !== 'string') return ''
  const trimmed = images.trim()
  if (!trimmed) return ''
  try {
    const parsed = JSON.parse(trimmed)
    return Array.isArray(parsed) ? parsed.filter(Boolean).map(toAbsoluteImageUrl).join('|||') : toAbsoluteImageUrl(trimmed)
  } catch {
    return trimmed.split('|||').map(item => toAbsoluteImageUrl(item.trim())).filter(Boolean).join('|||')
  }
}

export function legacyToJsonImages(image?: string): string {
  const items = (image || '').split('|||').map(item => item.trim()).filter(Boolean).map(stripImageBaseUrl)
  return JSON.stringify(items)
}

export function legacyToImageList(image?: string): string[] {
  return (image || '').split('|||').map(item => item.trim()).filter(Boolean).map(stripImageBaseUrl)
}

function toAbsoluteImageUrl(url: string): string {
  if (!url || /^https?:\/\//i.test(url) || url.startsWith('blob:') || url.startsWith('data:')) return url
  return `${API_BASE_URL}${url.startsWith('/') ? url : `/${url}`}`
}

function stripImageBaseUrl(url: string): string {
  return url.startsWith(API_BASE_URL) ? url.slice(API_BASE_URL.length) : url
}

export function canteenToLegacy(raw: any): Canteen {
  return {
    id: raw.id,
    name: raw.name,
    image: imagesToLegacy(raw.images ?? raw.image),
    location: raw.location || '',
    description: raw.description || '',
    sort_order: raw.sortOrder ?? raw.sort_order ?? 0,
    status: raw.status === 'open' ? 'active' : 'inactive',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function canteenToApi(data: Partial<Canteen>) {
  return {
    name: data.name,
    images: legacyToJsonImages(data.image),
    location: data.location,
    description: data.description,
    sortOrder: data.sort_order,
    status: data.status === 'inactive' ? 'closed' : 'open',
  }
}

export function stallToLegacy(raw: any): Stall {
  return {
    id: raw.id,
    canteen_id: raw.canteenId ?? raw.canteen_id,
    name: raw.name,
    image: imagesToLegacy(raw.images ?? raw.image),
    location: raw.location || '',
    description: raw.description || '',
    avg_rating: raw.avgRating ?? raw.avg_rating ?? 0,
    sort_order: raw.sortOrder ?? raw.sort_order ?? 0,
    status: raw.status === 'open' ? 'active' : 'inactive',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function stallToApi(data: Partial<Stall>) {
  return {
    canteenId: data.canteen_id,
    name: data.name,
    images: legacyToJsonImages(data.image),
    location: data.location,
    description: data.description,
    avgRating: data.avg_rating,
    sortOrder: data.sort_order,
    status: data.status === 'inactive' ? 'closed' : 'open',
  }
}

export function dishToLegacy(raw: any): Dish {
  const priceInCents = raw.price ?? 0
  return {
    id: raw.id,
    stall_id: raw.stallId ?? raw.stall_id,
    name: raw.name,
    image: imagesToLegacy(raw.images ?? raw.image),
    price: Math.round(priceInCents) / 100,
    tags: raw.tags || '',
    description: raw.description || '',
    avg_rating: raw.avgRating ?? raw.avg_rating ?? 0,
    rating_count: raw.ratingCount ?? raw.rating_count ?? 0,
    favorite_count: raw.collectCount ?? raw.favoriteCount ?? raw.favorite_count ?? 0,
    view_count: raw.viewCount ?? raw.view_count ?? 0,
    status: raw.status === 'on' ? 'active' : 'inactive',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function dishToApi(data: Partial<Dish>) {
  return {
    stallId: data.stall_id,
    name: data.name,
    price: data.price === undefined ? undefined : Math.round(Number(data.price) * 100),
    description: data.description,
    images: legacyToImageList(data.image),
    tags: data.tags,
    status: data.status === 'inactive' ? 'off' : 'on',
  }
}

export function reviewToLegacy(raw: any): Review {
  return {
    id: raw.id,
    user_id: raw.userId ?? raw.user_id,
    dish_id: raw.dishId ?? raw.dish_id,
    rating: raw.rating,
    content: raw.content || '',
    images: imagesToLegacy(raw.images),
    is_hidden: raw.isHidden ?? raw.is_hidden ?? 0,
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function userToLegacy(raw: any): User {
  return {
    id: raw.id,
    username: raw.username,
    password: '',
    nickname: raw.nickname || '',
    avatar: raw.avatar || '',
    stall_id: raw.stallId ?? raw.stall_id,
    role: raw.role,
    status: raw.status,
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

function toDate(value: unknown): Date {
  if (value instanceof Date) return value
  if (typeof value === 'string' || typeof value === 'number') return new Date(value)
  return new Date()
}
