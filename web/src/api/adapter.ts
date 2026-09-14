import type { Canteen, Dish, Review, SecState, Stall, User } from '@/types'
import { API_BASE_URL } from './config'

type PageLike<T> = T[] | { records?: T[]; list?: T[] }

export function pageRecords<T>(data: PageLike<T>): T[] {
  return Array.isArray(data) ? data : data.records || data.list || []
}

/**
 * dish.tags 统一读写格式：CSV 逗号分隔串（权威契约）。
 * 依据：schema.sql「标签，逗号分隔」、DishPublishReq.tags 为 String、
 * DishMapper FIND_IN_SET / DishServiceImpl split(",")、DishAdminController 示例 "tags": "recommended"。
 * parseTags 容错兼容历史脏数据（旧实现曾误写 JSON 数组串），展示时自动归一。
 */
export function parseTags(tags: unknown): string[] {
  if (Array.isArray(tags)) return tags.map(t => String(t).trim()).filter(Boolean)
  const s = typeof tags === 'string' ? tags.trim() : ''
  if (!s) return []
  if (s.startsWith('[')) {
    try {
      const parsed = JSON.parse(s)
      if (Array.isArray(parsed)) return parsed.map(t => String(t).trim()).filter(Boolean)
    } catch { /* 非合法 JSON，按 CSV 继续解析 */ }
  }
  return s.split(',').map(t => t.trim()).filter(Boolean)
}

/** string[] → CSV 逗号分隔串（写库格式，写侧统一出口） */
export function formatTags(tags: string[]): string {
  return tags.map(t => t.trim()).filter(Boolean).join(',')
}

/**
 * 图片字段容错解析：string[] / JSON 数组串 / ||| 分隔串 → string[]（绝对 URL）。
 * COS 公网地址原样返回；相对路径补 API_BASE_URL（dev 下防 Vite 源 404）。
 */
export function imagesToList(images: unknown): string[] {
  if (Array.isArray(images)) return images.filter(Boolean).map(toAbsoluteImageUrl)
  if (typeof images !== 'string') return []
  const trimmed = images.trim()
  if (!trimmed) return []
  try {
    const parsed = JSON.parse(trimmed)
    if (Array.isArray(parsed)) return parsed.filter(Boolean).map(toAbsoluteImageUrl)
  } catch { /* 非合法 JSON，按分隔串继续解析 */ }
  return trimmed.split('|||').map(item => toAbsoluteImageUrl(item.trim())).filter(Boolean)
}

/** imagesToList 的 legacy 串出口（||| 连接，供 image: string 旧字段沿用） */
export function imagesToLegacy(images: unknown): string {
  return imagesToList(images).join('|||')
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

/** 相对图片路径 → 绝对 URL（导出供上传预览等组件复用，dev 下相对路径会打到 Vite 源导致 404） */
export function toAbsoluteImageUrl(url: string): string {
  if (!url || /^https?:\/\//i.test(url) || url.startsWith('blob:') || url.startsWith('data:')) return url
  return `${API_BASE_URL}${url.startsWith('/') ? url : `/${url}`}`
}

function stripImageBaseUrl(url: string): string {
  return url.startsWith(API_BASE_URL) ? url.slice(API_BASE_URL.length) : url
}

/**
 * 食堂 → 前端模型（2026-09-14 Q-113 / PR-14 契约收紧）：
 * 食堂是**菜品筛选属性字典**，后端已移除 status / auditStatus / rejectReason（列即将 DROP），
 * 故不再做 status 映射（原 `active/inactive` 派生意已随契约作废）。
 */
export function canteenToLegacy(raw: any): Canteen {
  return {
    id: raw.id,
    name: raw.name,
    image: imagesToLegacy(raw.images ?? raw.image),
    location: raw.location || '',
    description: raw.description || '',
    sort_order: raw.sortOrder ?? raw.sort_order ?? 0,
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

/** 食堂 → 接口 payload（属性字典只「新增 / 改名」，不提交任何状态字段） */
export function canteenToApi(data: Partial<Canteen>) {
  return compactPayload({
    name: data.name,
    images: data.image === undefined ? undefined : legacyToJsonImages(data.image),
    location: data.location,
    description: data.description,
    sortOrder: data.sort_order,
  })
}

/**
 * 档口 → 前端模型（2026-09-14 Q-113 / PR-14 契约收紧）：档口同为**菜品筛选属性字典**，
 * 后端已移除 status / auditStatus / rejectReason（列即将 DROP），故不再做 status 映射；
 * floor（楼层）/ windowNo（窗口号）保留（端上有消费）。
 */
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
    floor: raw.floor || '',
    windowNo: raw.windowNo || '',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

/** 档口 → 接口 payload（属性字典只「新增 / 改名」，不提交任何状态字段） */
export function stallToApi(data: Partial<Stall>) {
  return compactPayload({
    canteenId: data.canteen_id,
    name: data.name,
    images: data.image === undefined ? undefined : legacyToJsonImages(data.image),
    location: data.location,
    description: data.description,
    sortOrder: data.sort_order,
    floor: data.floor,
    windowNo: data.windowNo,
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
    alias: raw.alias || '',
    avg_rating: raw.avgRating ?? raw.avg_rating ?? 0,
    rating_count: raw.ratingCount ?? raw.rating_count ?? 0,
    view_count: raw.viewCount ?? raw.view_count ?? 0,
    status: raw.status === 'on' ? 'active' : 'inactive',
    spiceLevel: raw.spiceLevel ?? 0,
    region: raw.region || '',
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
    alias: data.alias,
    images: data.image === undefined ? undefined : legacyToImageList(data.image),
    tags: data.tags,
    status: data.status === undefined ? undefined : (data.status === 'inactive' ? 'off' : 'on'),
    auditStatus: data.audit_status,
    spiceLevel: data.spiceLevel,
    region: data.region,
    originalPrice: data.originalPrice === undefined ? undefined : Math.round(Number(data.originalPrice) * 100),
    promoPrice: data.promoPrice === undefined || data.promoPrice === null ? null : Math.round(Number(data.promoPrice) * 100),
  })
}

/**
 * 安检状态归一化：仅接受后端 ReviewAdminVO.secState 的 'review'/'rejected'，
 * 其余（缺省/脏值，含旧后端未返回字段）按 'pass' 处理——存量评价均已过安检，不得误标待复核。
 */
export function normalizeSecState(v: unknown): SecState {
  return v === 'review' || v === 'rejected' ? v : 'pass'
}

/** 筛选下拉字符串 → 服务端 secState 查询参数（白名单收窄：非法/空值归 ''，即全部不透传） */
export function toSecFilter(v: unknown): SecState | '' {
  return v === 'review' || v === 'rejected' || v === 'pass' ? v : ''
}

export function reviewToLegacy(raw: any): Review {
  return {
    id: raw.id,
    user_id: raw.userId ?? raw.user_id,
    dish_id: raw.dishId ?? raw.dish_id,
    rating: raw.rating,
    content: raw.content || '',
    images: imagesToList(raw.images),
    secState: normalizeSecState(raw.secState ?? raw.sec_state),
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
    wechatBound: raw.wechatBound ?? (raw.openid ? true : false),
    bindEmail: (raw.bindEmail ?? raw.bind_email) || '',
    guestShortId: (raw.guestShortId ?? raw.guest_short_id) || '',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

function toDate(value: unknown): Date {
  if (value instanceof Date) return value
  if (typeof value === 'string' || typeof value === 'number') return new Date(value)
  return new Date()
}
