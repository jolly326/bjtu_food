import type { Dish, DishDetail, DishQuery } from '@/types/dish'
import { get } from './http'
// ==================== Mock 离线回退数据 ====================
const TAG_MAP: Record<string, string> = { 'recommended': '必吃推荐', 'signature': '招牌菜' }

function mockDish(id: number, name: string, price: number, rating: number, count: number, tags: string[], desc: string, canteen: string, stall: string): Dish {
  return { id, name, price, image: '', images: ['', '', ''], rating, ratingCount: count, tags: tags.map(t => TAG_MAP[t] || t), description: desc, canteen, stallName: stall }
}

/** 以"分"为单位的 mock 菜品（API 合同约定），经 toDish 转成"元"供 UI 使用 */
export const MOCK_DISHES: Dish[] = [
  mockDish(1, '红烧牛肉面', 1500, 4.8, 256, ['recommended', 'signature'], '浓汤慢炖，牛肉酥烂', '第一食堂', '面面俱到'),
  mockDish(2, '黄焖鸡米饭', 1400, 4.6, 198, ['signature'], '鲜嫩多汁，下饭首选', '第一食堂', '黄焖世家'),
  mockDish(3, '麻辣香锅', 1800, 4.7, 312, ['recommended'], '自选食材，随心搭配', '第二食堂', '麻辣诱惑'),
  mockDish(4, '酸菜鱼', 2000, 4.9, 423, ['recommended', 'signature'], '活鱼现杀，酸爽开胃', '第二食堂', '渔味轩'),
  mockDish(5, '宫保鸡丁', 1400, 4.4, 134, ['recommended'], '酸甜微辣，经典川菜', '第二食堂', '川味轩'),
]

function toDish(raw: any): Dish {
  return {
    id: raw.id, name: raw.name,
    price: raw.price ? Math.round(raw.price / 100) : 0,
    image: raw.image || '',
    rating: raw.avgRating ?? raw.rating ?? 0, ratingCount: raw.ratingCount ?? 0, favoriteCount: raw.collectCount ?? 0,
    tags: raw.tags ? (Array.isArray(raw.tags) ? raw.tags.map((t: string) => TAG_MAP[t] || t) : raw.tags.split(',').map((t: string) => TAG_MAP[t] || t)) : [],
    description: raw.description || '',
    canteen: raw.canteenName || raw.canteen || '',
    stallName: raw.stallName || '',
  }
}

function toDishDetail(raw: any): DishDetail {
  return { ...toDish(raw), ratingDistribution: raw.ratingDistribution || [] }
}

// ==================== API（HTTP 优先，失败时 fallback 到 mock） ====================

export async function getRecommendList(): Promise<Dish[]> {
  try {
    const rawList: any[] = await get('/dishes/hot')
    return rawList.map(toDish)
  } catch {
    console.log('[dish] getRecommendList 失败，使用 mock')
    return MOCK_DISHES.map(toDish)
  }
}

export async function getStallDishes(_canteen: string, stallName: string): Promise<Dish[]> {
  try {
    const rawList: any[] = await get('/dishes', { keyword: stallName })
    return rawList.map(toDish)
  } catch {
    console.log('[dish] getStallDishes 失败，使用 mock')
    return MOCK_DISHES.filter(d => d.stallName === stallName).map(toDish)
  }
}

export async function searchDishes(query: DishQuery): Promise<Dish[]> {
  const params: Record<string, any> = {}
  if (query.keyword) params.keyword = query.keyword
  if (query.minPrice !== undefined) params.minPrice = query.minPrice * 100
  if (query.maxPrice !== undefined) params.maxPrice = query.maxPrice * 100
  if (query.sortBy === 'rating') params.sortBy = 'rating'
  if (query.sortBy === 'price') params.sortBy = 'price'

  try {
    const res: any = await get('/dishes', params)
    const list = res.records || res || []
    return list.map(toDish)
  } catch {
    console.log('[dish] searchDishes 失败，使用 mock')
    let fallback = [...MOCK_DISHES]
    if (query.keyword) fallback = fallback.filter(d => d.name.includes(query.keyword!) || d.stallName.includes(query.keyword!))
    if (query.minPrice !== undefined) fallback = fallback.filter(d => d.price >= query.minPrice! * 100)
    if (query.maxPrice !== undefined) fallback = fallback.filter(d => d.price <= query.maxPrice! * 100)
    if (query.sortBy === 'rating') fallback.sort((a, b) => b.rating - a.rating)
    if (query.sortBy === 'price') fallback.sort((a, b) => a.price - b.price)
    return fallback.map(toDish)
  }
}

export async function getDishDetail(id: number): Promise<DishDetail> {
  try {
    const raw: any = await get(`/dishes/${id}`)
    return toDishDetail(raw)
  } catch {
    console.log('[dish] getDishDetail 失败，使用 mock')
    const fallback = MOCK_DISHES.find(d => d.id === id)
    if (!fallback) throw new Error('菜品不存在')
    return toDishDetail({
      ...fallback,
      ratingDistribution: [{ star: 5, count: 156 }, { star: 4, count: 68 }, { star: 3, count: 22 }, { star: 2, count: 8 }, { star: 1, count: 2 }],
    })
  }
}
