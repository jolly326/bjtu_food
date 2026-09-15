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

/**
 * 菜品保存 payload（DishAdminReq 契约，§7.23 第 1 条）：
 *  - `stallId`：既有档口直接选中；
 *  - `stallName`：按名 upsert 档口（存在复用 / 不存在自动建档），有效时优先于 stallId；
 *  - `canteenName`：仅在 stallName 触发新建档口时被后端消费（按名 upsert 所属食堂）；
 *  - `categoryId`：所属品类（可空=未分类，null=清空）。
 * 价格等其余字段沿用 Dish（api 层 dishToApi 元→分）。
 */
export type DishSavePayload = Omit<Dish, 'id' | 'created_at' | 'updated_at'> & {
  canteenName?: string | null
  stallName?: string | null
  categoryId?: number | null
}

export async function create(data: DishSavePayload) {
  await post<void>('/admin/dishes', dishToApi(data))
}

export async function updateById(id: number, data: Partial<Dish>) {
  await put<void>(`/admin/dishes/${id}`, dishToApi(data))
}

export async function deleteById(id: number) {
  await del<void>(`/admin/dishes/${id}`)
}

/**
 * 原 `getById(id)` 封装（公开端点 GET /dishes/{id}）已于 DEV-04 收口移除：
 * Web 后台只经 /admin/**，且该公开端点只返回在售菜品 → 已下架菜品取不到名。
 * 反馈关联菜品名改由 FeedbackAdminVO.relatedDishName 提供（见 api/feedback.ts）。
 * 如需菜品详情，请从 /admin/dishes 聚合（getAll）或列表数据中取，勿再直连公开端点。
 */
