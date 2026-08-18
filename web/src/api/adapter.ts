import type { Canteen, Dish, Review, Stall, User, AuditVO, AdminUser } from '@/types'
import { API_BASE_URL } from './config'

type PageLike<T> = T[] | { records?: T[]; list?: T[] }

export function pageRecords<T>(data: PageLike<T>): T[] {
  return Array.isArray(data) ? data : data.records || data.list || []
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

function compactPayload<T extends Record<string, unknown>>(payload: T): Partial<T> {
  return Object.fromEntries(Object.entries(payload).filter(([, value]) => value !== undefined)) as Partial<T>
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
  return compactPayload({
    name: data.name,
    images: data.image === undefined ? undefined : legacyToJsonImages(data.image),
    location: data.location,
    description: data.description,
    sortOrder: data.sort_order,
    status: data.status === undefined ? undefined : (data.status === 'inactive' ? 'closed' : 'open'),
  })
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
    floor: raw.floor || '',
    windowNo: raw.windowNo || '',
    businessHours: raw.businessHours || '',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function stallToApi(data: Partial<Stall>) {
  return compactPayload({
    canteenId: data.canteen_id,
    name: data.name,
    images: data.image === undefined ? undefined : legacyToJsonImages(data.image),
    location: data.location,
    description: data.description,
    sortOrder: data.sort_order,
    status: data.status === undefined ? undefined : (data.status === 'inactive' ? 'closed' : 'open'),
    floor: data.floor,
    windowNo: data.windowNo,
    businessHours: data.businessHours,
  })
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
    view_count: raw.viewCount ?? raw.view_count ?? 0,
    status: raw.status === 'on' ? 'active' : 'inactive',
    spiceLevel: raw.spiceLevel ?? 0,
    portion: raw.portion ?? 0,
    servePeriod: raw.servePeriod || '',
    limited: raw.limited ?? 0,
    audit_status: raw.auditStatus ?? raw.audit_status,
    reject_reason: (raw.rejectReason ?? raw.reject_reason) || '',
    originalPrice: raw.originalPrice == null && raw.original_price == null
      ? undefined
      : Math.round((raw.originalPrice ?? raw.original_price)) / 100,
    promoPrice: raw.promoPrice == null && raw.promo_price == null
      ? undefined
      : Math.round((raw.promoPrice ?? raw.promo_price)) / 100,
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function dishToApi(data: Partial<Dish>) {
  return compactPayload({
    stallId: data.stall_id,
    name: data.name,
    price: data.price === undefined ? undefined : Math.round(Number(data.price) * 100),
    description: data.description,
    images: data.image === undefined ? undefined : legacyToImageList(data.image),
    tags: data.tags,
    status: data.status === undefined ? undefined : (data.status === 'inactive' ? 'off' : 'on'),
    auditStatus: data.audit_status,
    spiceLevel: data.spiceLevel,
    portion: data.portion,
    servePeriod: data.servePeriod,
    limited: data.limited,
    originalPrice: data.originalPrice === undefined ? undefined : Math.round(Number(data.originalPrice) * 100),
    promoPrice: data.promoPrice === undefined || data.promoPrice === null ? null : Math.round(Number(data.promoPrice) * 100),
  })
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
    role: raw.role,
    status: raw.status,
    // task-02 新增：微信登录体系字段（snake_case 仅在 adapter 内部兜底）
    verified: raw.verified ?? 0,
    openid: raw.openid || '',
    bindEmail: (raw.bindEmail ?? raw.bind_email) || '',
    guestShortId: (raw.guestShortId ?? raw.guest_short_id) || '',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function auditToLegacy(raw: any): AuditVO {
  return {
    id: raw.id,
    type: raw.type,
    name: raw.name || '',
    price: raw.price !== undefined && raw.price !== null ? Math.round(raw.price) / 100 : undefined,
    images: imagesToLegacy(raw.images ?? raw.image),
    description: raw.description || raw.location || '',
    location: raw.location || '',
    submitterId: raw.submitterId ?? raw.submitter_id ?? raw.createdBy ?? raw.created_by,
    submitterName: (raw.submitterName ?? raw.submitter_name) || '',
    audit_status: (raw.auditStatus ?? raw.audit_status) || 'pending',
    reject_reason: (raw.rejectReason ?? raw.reject_reason) || '',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function adminUserToLegacy(raw: any): AdminUser {
  return {
    id: raw.id,
    username: raw.username,
    nickname: raw.nickname || '',
    role: raw.role || 'admin',
    status: raw.status || 'active',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

function toDate(value: unknown): Date {
  if (value instanceof Date) return value
  if (typeof value === 'string' || typeof value === 'number') return new Date(value)
  return new Date()
}
