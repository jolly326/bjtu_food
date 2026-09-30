import { get, post, put, del } from './http'
import type { DishDimensionVO, DishDimensionSaveReq } from '@/types/common'

/** B-06 菜品属性维度：不分页（量级个位数）。 */
export function listDimensions(): Promise<DishDimensionVO[]> {
  return get<DishDimensionVO[]>('/admin/dish-dimensions')
}

export function createDimension(req: DishDimensionSaveReq): Promise<DishDimensionVO> {
  return post<DishDimensionVO>('/admin/dish-dimensions', req)
}

export function updateDimension(
  id: number,
  req: DishDimensionSaveReq,
): Promise<DishDimensionVO> {
  return put<DishDimensionVO>(`/admin/dish-dimensions/${id}`, req)
}

export function deleteDimension(id: number): Promise<null> {
  return del<null>(`/admin/dish-dimensions/${id}`)
}
