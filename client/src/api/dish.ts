import type {
  Dish, DishDetail, DishQuery, DishSortBy,
  Suggestion, HotSearch,
} from '@/types/dish'
import { get, del, post } from './http'
import { fenToYuan, yuanToFen } from '@/utils/money'
import { getImageUrl } from '@/utils/image'

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

type PageLike<T> = T[] | { records?: T[]; list?: T[]; total?: number }

function recordsOf<T>(value: PageLike<T> | undefined | null): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  return value.records || value.list || []
}

function totalOf(value: PageLike<any> | undefined | null): number {
  if (!value) return 0
  if (Array.isArray(value)) return value.length
  return typeof value.total === 'number' ? value.total : recordsOf(value).length
}

function normalizeBoolean(value: unknown): boolean {
  return value === true || value === 1 || value === '1'
}

function normalizeImages(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string' && item.length > 0).map(getImageUrl)
  if (typeof value !== 'string' || !value.trim()) return []
  const text = value.trim()
  try {
    const parsed = JSON.parse(text)
    return Array.isArray(parsed) ? normalizeImages(parsed) : [getImageUrl(text)]
  } catch {
    return text.split('|||').map(item => item.trim()).filter(Boolean).map(getImageUrl)
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
    price: fenToYuan(raw.price),
    image: images[0] || '',
    images,
    rating: raw.avgRating ?? raw.rating ?? 0,
    ratingCount: raw.ratingCount ?? raw.rating_count ?? 0,
    tags: tags.map((t: string) => TAG_MAP[t] || t),
    description: raw.description || '',
    canteen: raw.canteenName || raw.canteen || '',
    stallName: raw.stallName || '',
    stallId: raw.stallId != null ? Number(raw.stallId) : undefined,
    isNew: !!raw.isNew,
    hasReviewed: !!raw.hasReviewed,
    auditStatus: raw.auditStatus ?? raw.audit_status,
    // ===== task-03 位置链路（来自 stall 联表） =====
    floor: raw.floor || '',
    windowNo: raw.windowNo || '',
    businessHours: raw.businessHours || '',
    // ===== task-03 属性标签（来自 dish） =====
    spiceLevel: raw.spiceLevel ?? raw.spice_level,
    portion: raw.portion,
    servePeriod: raw.servePeriod || raw.serve_period || '',
    limited: normalizeBoolean(raw.limited ?? raw.is_limited),
    // 折扣价（分→元，仅展示层转换；task-12.9）
    originalPrice: raw.originalPrice != null ? fenToYuan(raw.originalPrice) : undefined,
    promoPrice: raw.promoPrice != null ? fenToYuan(raw.promoPrice) : undefined,
    createdBy: raw.createdBy != null ? Number(raw.createdBy) : undefined,
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

/** 档口菜品：按 stallId 精确过滤（后端 GET /dishes 已支持 stallId，避免 keyword 模糊搜索召回同名菜品） */
export async function getStallDishes(stallId: number): Promise<Dish[]> {
  if (!stallId) return []
  const res = await get<any>('/dishes', { stallId, page: 1, pageSize: 50 })
  return recordsOf<any>(res).map(toDish)
}

/**
 * 通用菜品检索（task-02 多维筛选结果页 + task-01 首页无限加载）
 * 复用 GET /dishes，支持 keyword / canteenId / tag / minPrice / maxPrice / sortBy / sortOrder / page / pageSize。
 * 金额 minPrice/maxPrice 由前端「元」在 API 层转「分」提交（§3.x 金额红线）。
 * sortBy 取值（ARCH §3.1）：heat / rating / price / created_at / collects。
 * 返回分页结果（list + total），供瀑布流无限加载去重与触底判断。
 */
export async function searchDishesPage(query: DishQuery): Promise<{ list: Dish[]; total: number }> {
  const params: Record<string, any> = {
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.canteenId != null) params.canteenId = query.canteenId
  if (query.tag) params.tag = query.tag
  if (query.spiceLevel != null) params.spiceLevel = query.spiceLevel
  if (query.minPrice != null) params.minPrice = yuanToFen(query.minPrice)
  if (query.maxPrice != null) params.maxPrice = yuanToFen(query.maxPrice)
  if (query.sortBy) params.sortBy = query.sortBy
  if (query.sortOrder) params.sortOrder = query.sortOrder

  const res = await get<any>('/dishes', params)
  const list = recordsOf<any>(res).map(toDish)
  return { list, total: totalOf(res) }
}

/** 兼容旧调用：返回平铺 Dish[]（find 页历史用法保留） */
export async function searchDishes(query: DishQuery): Promise<Dish[]> {
  return (await searchDishesPage(query)).list
}

export async function getDishDetail(id: number): Promise<DishDetail> {
  const raw = await get<any>(`/dishes/${id}`)
  return toDishDetail(raw)
}

/**
 * 上报菜品浏览（POST /dishes/{id}/view，供 view_count / 热度排序 / 猜你喜欢使用）。
 * 后端 addView 通过 token 取当前用户（SecurityUtil.getCurrentUserId），无需 body；
 * 需登录态（/dishes/** 仅 GET 公开）。浏览埋点属非关键链路，失败静默。
 */
export async function addView(id: number): Promise<void> {
  try {
    await post<void>(`/dishes/${id}/view`)
  } catch {
    /* 静默失败：浏览统计不应阻塞详情展示 */
  }
}

/** 删除本人发布的菜品（STU 仅 created_by 本人，task-12.5） */
export async function deleteDish(id: number): Promise<void> {
  await del<void>(`/dishes/${id}`)
}

export async function getNewDishes(): Promise<Dish[]> {
  return recordsOf<any>(await get('/dishes/new')).map(toDish)
}

export async function getPromotionDishes(): Promise<Dish[]> {
  return recordsOf<any>(await get('/dishes/promotions')).map(toDish)
}

/** 新晋黑马（task-02：GET /dishes/rising，近 14 天热度增速 TOP10） */
export async function getRisingDishes(): Promise<Dish[]> {
  return recordsOf<any>(await get('/dishes/rising')).map(toDish)
}

/** 热搜 TOP10（task-02：GET /dishes/hot-search，一期为菜品热度派生的热门词条） */
export async function getHotSearch(): Promise<HotSearch[]> {
  const raw = await get<any[]>('/dishes/hot-search')
  return (raw || []).map((item: any) => ({
    keyword: item.keyword || '',
    heat: Number(item.heat ?? 0),
    relatedCount: Number(item.relatedCount ?? 0) || undefined,
  }))
}

/** 搜索联想（task-02：GET /dishes/suggest，混合菜品/档口/食堂） */
export async function getSuggestions(keyword: string): Promise<Suggestion[]> {
  if (!keyword || !keyword.trim()) return []
  const raw = await get<any[]>('/dishes/suggest', { keyword: keyword.trim() })
  return (raw || []).map((item: any) => ({
    type: (item.type || 'dish') as Suggestion['type'],
    id: Number(item.id ?? 0),
    name: item.name || '',
    image: getImageUrl(item.image || ''),
    // 档口需携带所属食堂名（后端 suggest 已联表返回 canteen），跳档口详情要 navParams.canteen
    canteen: item.canteen || undefined,
    // 价格：分 → 元（§3.x 金额红线：转换必须在 api 层统一，页面模板禁裸 /100）
    price: item.price != null ? fenToYuan(item.price) : undefined,
    rating: item.rating != null ? Number(item.rating) : undefined,
    ratingCount: item.ratingCount != null ? Number(item.ratingCount) : undefined,
  }))
}

/** 首页热门瀑布流首屏：复用 /dishes/hot（公开 TOP 列表；可选 lat/lng 按距离加权排序） */
export async function getHomeHotDishes(limit = 20, lat?: number | null, lng?: number | null): Promise<Dish[]> {
  const params: Record<string, unknown> = { limit }
  if (typeof lat === 'number' && typeof lng === 'number') {
    params.lat = lat
    params.lng = lng
  }
  const res = await get<any>('/dishes/hot', params)
  return recordsOf<any>(res).map(toDish)
}

/** 首页热门瀑布流：无限加载分页走 /dishes?sortBy=heat&sortOrder=desc */
export async function getHotDishesPage(page: number, pageSize = 20): Promise<{ list: Dish[]; total: number }> {
  return searchDishesPage({ sortBy: 'heat', sortOrder: 'desc', page, pageSize })
}

export type { DishSortBy }
