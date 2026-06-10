import type { Canteen } from '@/types'
import { del, get, post, put } from './http'
import { canteenToApi, canteenToLegacy } from './adapter'

export async function getAll(): Promise<Canteen[]> {
  return (await get<any[]>('/admin/canteens')).map(canteenToLegacy)
}

export async function create(data: Omit<Canteen, 'id' | 'created_at' | 'updated_at'>) {
  await post<void>('/admin/canteens', canteenToApi(data))
}

export async function updateById(id: number, data: Partial<Canteen>) {
  await put<void>(`/admin/canteens/${id}`, canteenToApi(data))
}

export async function deleteById(id: number) {
  await del<void>(`/admin/canteens/${id}`)
}
