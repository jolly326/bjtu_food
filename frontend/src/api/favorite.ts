import type { Dish } from '@/types/dish'
import { get, post } from './http'
import { toDish } from './dish'

type PageLike<T> = T[] | { records?: T[]; list?: T[] }

function recordsOf<T>(value: PageLike<T> | undefined | null): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  return value.records || value.list || []
}

export async function getFavoriteList(): Promise<Dish[]> {
  const res = await get<any>('/favorites', { page: 1, pageSize: 50 })
  return recordsOf<any>(res).map(toDish)
}

async function toggleFavorite(dishId: number): Promise<boolean> {
  const res = await post<{ favorited: boolean }>('/favorites/toggle', { dishId })
  return !!res?.favorited
}

export async function addFavorite(dishId: number): Promise<void> {
  await toggleFavorite(dishId)
}

export async function removeFavorite(dishId: number): Promise<void> {
  await toggleFavorite(dishId)
}
