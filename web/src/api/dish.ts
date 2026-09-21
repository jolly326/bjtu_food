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
 *  - `canteenName`：仅在 stallName 触发新建档口时被后端消费（按名 upsert 所属食堂）。
 * 价格等其余字段沿用 Dish（api 层 dishToApi 元→分）。
 */
export type DishSavePayload = Omit<Dish, 'id' | 'created_at' | 'updated_at'> & {
  canteenName?: string | null
  stallName?: string | null
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

/** 菜品大类字典项（`GET /dishes/meal-types` 单行出参，2026-09-21 §7.34）。 */
export interface MealTypeDictItem {
  /** 大类枚举键（写入 `DishAdminReq.mealType` 用的值） */
  key: string
  /** 中文标签（端上直接渲染，**端上不得另行维护任何 key → 中文 映射**） */
  label: string
  /** 展示顺序（后端已按升序下发） */
  order: number
}

/**
 * 菜品大类字典（公开端点 `GET /dishes/meal-types`）。
 *
 * - 标签文案 / 顺序 / 集合的**唯一真源在后端**（`MealTypeConst`）→ Web 端零硬编码中文，选项直接渲染本响应；
 * - 端点只下发**当前有在售菜品**的大类（空类自动隐藏、有菜自动出现）→ 管理端下拉 / 筛选若需覆盖
 *   已下架菜品所在的大类，由调用方按需用列表数据兜底（见 `stores/mealTypeStore.ts` 口径说明）。
 * - 出参字段本身即 camelCase，故此处只做形状与空值归一，不做下划线→驼峰映射。
 */
export async function listMealTypes(): Promise<MealTypeDictItem[]> {
  const data: any = await get<any[]>('/dishes/meal-types')
  const rows = Array.isArray(data) ? data : []
  return rows
    .map(raw => ({
      key: String(raw?.key ?? ''),
      label: String(raw?.label ?? ''),
      order: Number(raw?.order ?? 0),
    }))
    .filter(item => item.key && item.label)
    .sort((a, b) => a.order - b.order)
}

/**
 * 原 `getById(id)` 封装（公开端点 GET /dishes/{id}）已于 DEV-04 收口移除：
 * Web 后台只经 /admin/**，且该公开端点只返回在售菜品 → 已下架菜品取不到名。
 * 反馈关联菜品名改由 FeedbackAdminVO.relatedDishName 提供（见 api/feedback.ts）。
 * 如需菜品详情，请从 /admin/dishes 聚合（getAll）或列表数据中取，勿再直连公开端点。
 */
