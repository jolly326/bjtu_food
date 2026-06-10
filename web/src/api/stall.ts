import type { Stall } from '@/types'
import { del, get, post, put } from './http'
import { stallToApi, stallToLegacy } from './adapter'

export async function getAll(): Promise<Stall[]> {
  return (await get<any[]>('/admin/stalls')).map(stallToLegacy)
}

export async function create(data: Omit<Stall, 'id' | 'created_at' | 'updated_at'>) {
  await post<void>('/admin/stalls', stallToApi(data))
}

export async function updateById(id: number, data: Partial<Stall>) {
  await put<void>(`/admin/stalls/${id}`, stallToApi(data))
}

export async function deleteById(id: number) {
  await del<void>(`/admin/stalls/${id}`)
}
