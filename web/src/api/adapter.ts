import type { Canteen, Dish, Review, Stall, User } from '@/types'
import { parseCsv } from '@/constants'
import { API_BASE_URL } from './config'

/**
 * 分页信封（管理端列表接口统一形态）：`records` 行 + `total` 总数；
 * 兼容后端直接返回裸数组的形态（契约精简 §7.33：PageResult 只输出 records）。
 * 用 `unknown` 而非 `any`：行数据在适配层（`xxxToLegacy`）内做逐字段归一，此处不必放行 any。
 */
export type PageEnvelope<T> = T[] | { records?: T[]; total?: number }

/**
 * 提取分页行数据（信封 / 裸数组均可）。
 */
export function pageRecords<T>(data: PageEnvelope<T>): T[] {
  return Array.isArray(data) ? data : data.records || []
}

/* 注（§7.29 / §7.28）：`dish.tags` 标签字段不提供，parseTags / formatTags 随之移除。
 *
 * （§7.40 R4 / change dish-detail-contract-hardening）：`ingredients` / `flavorTags`
 * 的存储由「CSV 逗号分隔串」改为 **JSON 数组**，后端出参随之改为 `string[]` →
 * 读侧经 `parseCsv` 归一（**兼容两种形态**，历史脏值不炸），写侧**直接提交数组**（不再 `formatCsv`）。 */

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

/* ============================================================
 * 后端原始行类型（替代此前的 `raw: any`）
 * ------------------------------------------------------------
 * · 适配层同时兼容 **camelCase（现行）** 与 **snake_case（历史响应）** 两种键名，
 *   故两种命名都列为可选字段；未出现的键在取值侧已有 `??` 与 `||` 兜底。
 * · 值类型放宽（可空 / 可为字符串数字），由本层做归一 —— 不放行 `any`。
 * ============================================================ */
// 命名与取值约定：
// · `RawNum` —— 数值键（id / 外键 / 计数 / 评分 / 价格「分」），JSON 侧为 number；
// · `RawText` —— 可为 null 的文本键（取值侧一律带 `|| ''` 兜底）；
// · 直接赋给非空目标字段的键（如 name / username / status）声明为**非空**，避免额外兜底。
type RawNum = number
type RawText = string | null
type RawTime = string | null

export interface RawCanteen {
  id?: RawNum
  name?: string
  images?: unknown
  image?: unknown
  location?: RawText
  description?: RawText
  sortOrder?: RawNum
  sort_order?: RawNum
  createdAt?: RawTime
  created_at?: RawTime
  updatedAt?: RawTime
  updated_at?: RawTime
}

export interface RawStall {
  id?: RawNum
  canteenId?: RawNum
  canteen_id?: RawNum
  name?: string
  images?: unknown
  image?: unknown
  location?: RawText
  description?: RawText
  avgRating?: RawNum
  avg_rating?: RawNum
  sortOrder?: RawNum
  sort_order?: RawNum
  floor?: RawText
  windowNo?: RawText
  createdAt?: RawTime
  created_at?: RawTime
  updatedAt?: RawTime
  updated_at?: RawTime
}

export interface RawDish {
  id?: RawNum
  stallId?: RawNum
  stall_id?: RawNum
  name?: string
  images?: unknown
  image?: unknown
  price?: RawNum
  description?: RawText
  avgRating?: RawNum
  avg_rating?: RawNum
  ratingCount?: RawNum
  rating_count?: RawNum
  status?: RawText
  stallName?: RawText
  stall_name?: RawText
  canteenName?: RawText
  canteen_name?: RawText
  originalPrice?: RawNum
  original_price?: RawNum
  dietType?: RawText
  diet_type?: RawText
  ingredients?: unknown
  ingredients_json?: unknown
  flavorTags?: unknown
  flavor_tags?: unknown
  serveTemp?: RawText
  serve_temp?: RawText
  mealType?: RawText
  meal_type?: RawText
  createdAt?: RawTime
  created_at?: RawTime
  updatedAt?: RawTime
  updated_at?: RawTime
}

export interface RawReview {
  id?: RawNum
  userId?: RawNum
  user_id?: RawNum
  dishId?: RawNum
  dish_id?: RawNum
  rating?: RawNum
  content?: RawText
  images?: unknown
  isHidden?: RawNum
  is_hidden?: RawNum
  createdAt?: RawTime
  created_at?: RawTime
}

export interface RawUser {
  id?: RawNum
  username?: string
  nickname?: RawText
  avatar?: RawText
  status?: string
  wechatBound?: boolean | null
  openid?: RawText
  bindEmail?: RawText
  bind_email?: RawText
  createdAt?: RawTime
  created_at?: RawTime
  updatedAt?: RawTime
  updated_at?: RawTime
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
 * 食堂 → 前端模型（Q-113 / PR-14 契约收紧）：
 * 食堂是**菜品筛选属性字典**，后端已移除 status / auditStatus / rejectReason，
 * 故不再做 status 映射（无 `active/inactive` 派生意）。
 */
export function canteenToLegacy(raw: RawCanteen): Canteen {
  return {
    id: raw.id ?? 0,
    name: raw.name ?? '',
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
 * 档口 → 前端模型（Q-113 / PR-14 契约收紧）：档口同为**菜品筛选属性字典**，
 * 后端已移除 status / auditStatus / rejectReason，故不再做 status 映射；
 * floor（楼层）/ windowNo（窗口号）保留（端上有消费）。
 */
export function stallToLegacy(raw: RawStall): Stall {
  return {
    id: raw.id ?? 0,
    canteen_id: raw.canteenId ?? raw.canteen_id ?? 0,
    name: raw.name ?? '',
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

export function dishToLegacy(raw: RawDish): Dish {
  return {
    id: raw.id ?? 0,
    stall_id: raw.stallId ?? raw.stall_id ?? 0,
    name: raw.name ?? '',
    image: imagesToLegacy(raw.images ?? raw.image),
    price: Math.round(raw.price ?? 0) / 100,
    description: raw.description || '',
    avg_rating: raw.avgRating ?? raw.avg_rating ?? 0,
    rating_count: raw.ratingCount ?? raw.rating_count ?? 0,
    status: raw.status === 'on' ? 'active' : 'inactive',
    stallName: raw.stallName || raw.stall_name || '',
    canteenName: raw.canteenName || raw.canteen_name || '',
    // 注（§7.23 第 4 条）：dish.audit_status / reject_reason 从前端契约移除（后端列为历史列，
    // DishAdminVO 不再返回，业务代码不再读写）。
    // 原价（§7.26）：展示值恒取 price，originalPrice > price 时才划线。
    originalPrice: raw.originalPrice == null && raw.original_price == null
      ? undefined
      : Math.round((raw.originalPrice ?? raw.original_price) ?? 0) / 100,
    // 描述四维（§7.28）：替代 spice_level / region（两字段不提供）。
    // 多值维（§7.40 R4）：后端已改为 string[] 直出；parseCsv 归一兼容历史逗号串 / JSON 串。
    dietType: raw.dietType || raw.diet_type || '',
    ingredients: parseCsv(raw.ingredients ?? raw.ingredients_json),
    flavorTags: parseCsv(raw.flavorTags ?? raw.flavor_tags),
    serveTemp: raw.serveTemp || raw.serve_temp || '',
    // 菜品大类（§7.34 / change home-ui-refresh）：DishAdminVO 出参透传枚举键。
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
 * 无「安检状态归一化 / 筛选白名单」导出函数（取消人工复核：pass/review 均放行、仅 risky 拒绝，
 * 后台不再读取该字段，后端字段同源移除）。
 */
export function reviewToLegacy(raw: RawReview): Review {
  return {
    id: raw.id ?? 0,
    user_id: raw.userId ?? raw.user_id ?? 0,
    dish_id: raw.dishId ?? raw.dish_id ?? 0,
    rating: raw.rating ?? 0,
    content: raw.content || '',
    images: imagesToList(raw.images),
    is_hidden: raw.isHidden ?? raw.is_hidden ?? 0,
    created_at: toDate(raw.createdAt || raw.created_at),
    // review.updated_at 后端列不返回 —— 不读取 raw.updatedAt（该键已不存在）
  }
}

export function userToLegacy(raw: RawUser): User {
  return {
    id: raw.id ?? 0,
    username: raw.username ?? '',
    password: '',
    nickname: raw.nickname || '',
    avatar: raw.avatar || '',
    status: raw.status ?? '',
    // 微信登录体系字段（snake_case 仅在 adapter 内部兜底）：
    // verified 不在出参，认证态由 bindEmail 非空派生（判据唯一真源）
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
