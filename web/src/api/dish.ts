import type { Dish } from '@/types'
import { del, get, post, put } from './http'
import { dishToApi, dishToLegacy, pageRecords } from './adapter'

/**
 * 菜品全量采集（聚合页 / 详情页联查等需要完整集合的场景）。
 * 后端 GET /admin/dishes 为分页 IPage（{records,total,...}，page/pageSize 透传，
 * PageUtil 单页上限 100），按 page 循环拉取直到取完，避免只取首页导致列表静默截断（WEB-104）。
 */
export async function getAll(): Promise<Dish[]> {
  const all: Dish[] = []
  let page = 1
  const pageSize = 100
  for (let guard = 0; guard < 1000; guard++) {
    const data: any = await get<any>('/admin/dishes', { page, pageSize })
    const records = pageRecords(data).map(dishToLegacy)
    if (!records.length) break
    all.push(...records)
    if (records.length < pageSize) break
    page++
  }
  return all
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

/** 菜品详情（公开端点 GET /dishes/{id}，含 stallId/canteenId 联表），用于反馈关联跳转档口链路（见 change prelaunch-loop-closure 10.3） */
export async function getById(id: number): Promise<{ id: number; stallId?: number; canteenId?: number; name?: string }> {
  const raw: any = await get(`/dishes/${id}`)
  return { id: raw.id, stallId: raw.stallId, canteenId: raw.canteenId, name: raw.name }
}
