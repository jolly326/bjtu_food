import type { Dish } from '@/types/dish'
import { get, post } from './http'

const MOCK_FAVORITES: Dish[] = [
  { id: 1, name: '红烧牛肉面', price: 15, image: '', rating: 4.8, ratingCount: 256, tags: ['必吃推荐', '招牌菜'], description: '浓汤慢炖，牛肉酥烂', canteen: '第一食堂', stallName: '面面俱到' },
  { id: 4, name: '酸菜鱼', price: 20, image: '', rating: 4.9, ratingCount: 423, tags: ['必吃推荐', '招牌菜'], description: '活鱼现杀，酸爽开胃', canteen: '第二食堂', stallName: '渔味轩' },
]

export async function getFavoriteList(): Promise<Dish[]> {
  try {
    const res: any = await get('/favorites', { page: 1, pageSize: 50 })
    const list = res.records || res || []
    return list
  } catch {
    console.log('[favorite] getFavoriteList 失败，使用 mock')
    return MOCK_FAVORITES
  }
}

export async function addFavorite(dishId: number): Promise<void> {
  try {
    await post('/favorites/toggle', { dishId })
  } catch {
    console.log('[favorite] addFavorite 失败')
    throw new Error('收藏失败')
  }
}

export async function removeFavorite(dishId: number): Promise<void> {
  try {
    await post('/favorites/toggle', { dishId })
  } catch {
    console.log('[favorite] removeFavorite 失败')
    throw new Error('取消收藏失败')
  }
}
