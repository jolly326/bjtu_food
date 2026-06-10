import type { Dish } from '@/types'
import { del, get, post, put } from './http'
import { dishToApi, dishToLegacy } from './adapter'

export async function getAll(): Promise<Dish[]> {
  return (await get<any[]>('/admin/dishes')).map(dishToLegacy)
}

export async function create(data: Omit<Dish, 'id' | 'created_at' | 'updated_at'>) {
  await post<void>('/admin/dishes', dishToApi(data))
}

export async function updateById(id: number, data: Partial<Dish>) {
  await put<void>(`/admin/dishes/${id}`, dishToApi(data))
}

export async function deleteById(id: number) {
  await del<void>(`/admin/dishes/${id}`)
}
