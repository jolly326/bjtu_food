import type { Dish } from '@/types'
import { del, get, post, put } from './http'
import { dishToApi, dishToLegacy, pageRecords } from './adapter'

export async function getAll(): Promise<Dish[]> {
  // 后端 listAllForAdmin 已改为分页 IPage（{records,total,...}），与全站其他 admin 列表一致，
  // 用 pageRecords 兼容数组/IPage 两种返回形态，避免对 IPage 直接 .map 崩溃。
  return pageRecords(await get<any>('/admin/dishes')).map(dishToLegacy)
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
