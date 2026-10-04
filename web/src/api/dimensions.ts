import { get, post, put, del } from './http'
import { submitSort } from './shared'
import type {
  DishDimensionAdminVO,
  DishDimensionSaveReq,
  DishValueAdminVO,
  DishValueSaveReq,
  SortItemsReq,
} from '@/types/common'

/* ==================== A4 维度 ==================== */

/** A4 维度列表（按 `order` 升序；**不分页**） */
export function listDimensions(): Promise<DishDimensionAdminVO[]> {
  return get<DishDimensionAdminVO[]>('/admin/dish-dimensions')
}

/** A4 新增维度（默认排最后；`fieldKey` 唯一） */
export function createDimension(req: DishDimensionSaveReq): Promise<DishDimensionAdminVO> {
  return post<DishDimensionAdminVO>('/admin/dish-dimensions', req)
}

/** A4 修改维度（`fieldKey` 在用后不可改；`valueType` 切换会自动迁移数据） */
export function updateDimension(id: number, req: DishDimensionSaveReq): Promise<null> {
  return put<null>(`/admin/dish-dimensions/${id}`, req)
}

/** A4 删除维度（被引用 → 400） */
export function deleteDimension(id: number): Promise<null> {
  return del<null>(`/admin/dish-dimensions/${id}`)
}

/** A4 维度排序（拖拽后**整体提交全量行**） */
export function sortDimensions(req: SortItemsReq): Promise<null> {
  return submitSort('/admin/dish-dimensions/sort', req)
}

/* ==================== A4 取值 ==================== */

/** A4 取值列表（按 `order` 升序） */
export function listValues(dimensionId: number): Promise<DishValueAdminVO[]> {
  return get<DishValueAdminVO[]>(`/admin/dish-dimensions/${dimensionId}/values`)
}

/** A4 新增取值（同维度下 `label` 唯一，应用层保证） */
export function createValue(dimensionId: number, req: DishValueSaveReq): Promise<DishValueAdminVO> {
  return post<DishValueAdminVO>(`/admin/dish-dimensions/${dimensionId}/values`, req)
}

/** A4 修改取值（只改 `label`） */
export function updateValue(
  dimensionId: number,
  valueId: number,
  req: DishValueSaveReq,
): Promise<null> {
  return put<null>(`/admin/dish-dimensions/${dimensionId}/values/${valueId}`, req)
}

/** A4 删除取值（被引用 → 400） */
export function deleteValue(dimensionId: number, valueId: number): Promise<null> {
  return del<null>(`/admin/dish-dimensions/${dimensionId}/values/${valueId}`)
}

/** A4 取值排序（拖拽后**整体提交全量行**） */
export function sortValues(dimensionId: number, req: SortItemsReq): Promise<null> {
  return submitSort(`/admin/dish-dimensions/${dimensionId}/values/sort`, req)
}
