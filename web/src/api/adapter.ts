import type { Canteen, Dish, Review, Stall, User } from '@/types'
import { API_BASE_URL } from './config'

type PageLike<T> = T[] | { records?: T[] }

/**
 * 提取分页行数据。
 * 2026-09-21 契约精简（§7.33）：服务端 `PageResult` 只输出 `records`，原 `list` 兼容字段已删除，
 * 故此处不再保留 `data.list` 兜底分支。
 */
export function pageRecords<T>(data: PageLike<T>): T[] {
  return Array.isArray(data) ? data : data.records || []
}

/* 注（2026-09-20 §7.29 / §7.28）：原 `dish.tags` 标签字段全链下线，原 parseTags / formatTags
 * 两个导出随之零消费删除。新的多值机器字段（ingredients / flavorTags，CSV 逗号分隔）读写格式
 * 收敛到 `constants/index.ts` 的 parseCsv / formatCsv（与四维选项字典同处，写侧统一出口）。 */

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
  return {
    id: raw.id,
    stall_id: raw.stallId ?? raw.stall_id,
    name: raw.name,
    image: imagesToLegacy(raw.images ?? raw.image),
    price: Math.round(raw.price ?? 0) / 100,
    description: raw.description || '',
    alias: raw.alias || '',
    avg_rating: raw.avgRating ?? raw.avg_rating ?? 0,
    rating_count: raw.ratingCount ?? raw.rating_count ?? 0,
    status: raw.status === 'on' ? 'active' : 'inactive',
    stallName: raw.stallName || raw.stall_name || '',
    canteenName: raw.canteenName || raw.canteen_name || '',
    // 注（§7.23 第 4 条，2026-09-15）：dish.audit_status / reject_reason 已随「菜品审核 UI 下线」
    // 从前端契约移除（后端列为退役历史列，DishAdminVO 不再返回，业务代码不再读写）。
    // 原价（§7.26）：promoPrice 已删除，展示值恒取 price，originalPrice > price 时才划线。
    originalPrice: raw.originalPrice == null && raw.original_price == null
      ? undefined
      : Math.round(raw.originalPrice ?? raw.original_price) / 100,
    // 描述四维（§7.28，2026-09-20）：替代原 spice_level / region（两字段已删）。
    dietType: raw.dietType || raw.diet_type || '',
    ingredients: raw.ingredients || '',
    flavorTags: raw.flavorTags || raw.flavor_tags || '',
    serveTemp: raw.serveTemp || raw.serve_temp || '',
    // 菜品大类（2026-09-21 §7.34 / change home-ui-refresh）：DishAdminVO 出参透传枚举键。
    // 中文标签的真源是后端字典 GET /dishes/meal-types（本层与视图层均不做 key→中文 映射）。
    mealType: raw.mealType || raw.meal_type || '',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

export function dishToApi(data: Partial<Dish>) {
  return compactPayload({
    stallId: data.stall_id,
    // 按名 upsert（§7.23 第 1 条）：stallName 有效时优先生效（存在复用/不存在自动建档）；
    // canteenName 仅在 stallName 触发新建档口时被后端消费（按名 upsert 所属食堂）
    stallName: data.stallName,
    canteenName: data.canteenName,
    name: data.name,
    price: data.price === undefined ? undefined : Math.round(Number(data.price) * 100),
    description: data.description,
    alias: data.alias,
    images: data.image === undefined ? undefined : legacyToImageList(data.image),
    status: data.status === undefined ? undefined : (data.status === 'inactive' ? 'off' : 'on'),
    // 描述四维（§7.28）：机器值 CSV / 单选值原样提交（空串 = 清空该维）
    dietType: data.dietType,
    ingredients: data.ingredients,
    flavorTags: data.flavorTags,
    serveTemp: data.serveTemp,
    // 菜品大类（§7.34）：枚举键原样提交（不在此做校验，后端白名单非法值即 400）；
    // undefined（如行内上下架的部分更新）经 compactPayload 剔除 → 后端语义为「不修改」。
    mealType: data.mealType,
    // null 显式携带 = 清空原价（WEB-102 折扣清空契约；0 分语义由 null 表达，禁止落 0）
    originalPrice: data.originalPrice === undefined
      ? undefined
      : data.originalPrice === null || Number(data.originalPrice) <= 0
        ? null
        : Math.round(Number(data.originalPrice) * 100),
  })
}

/**
 * 2026-09-15（取消人工复核）：原「安检状态归一化 / 筛选白名单」两个导出函数随内容安全检测策略调整退役
 * （pass/review 均放行、仅 risky 拒绝，后台不再读取该字段，后端字段同源移除）。
 */
export function reviewToLegacy(raw: any): Review {
  return {
    id: raw.id,
    user_id: raw.userId ?? raw.user_id,
    dish_id: raw.dishId ?? raw.dish_id,
    rating: raw.rating,
    content: raw.content || '',
    images: imagesToList(raw.images),
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
    status: raw.status,
    // task-02 新增：微信登录体系字段（snake_case 仅在 adapter 内部兜底）
    verified: raw.verified ?? 0,
    wechatBound: raw.wechatBound ?? (raw.openid ? true : false),
    bindEmail: (raw.bindEmail ?? raw.bind_email) || '',
    created_at: toDate(raw.createdAt || raw.created_at),
    updated_at: toDate(raw.updatedAt || raw.updated_at),
  }
}

function toDate(value: unknown): Date {
  if (value instanceof Date) return value
  if (typeof value === 'string' || typeof value === 'number') return new Date(value)
  return new Date()
}
