import { getPage, get, post, put, del } from './http'
import type { DishAdminVO, DishSaveReq, DishListParams } from '@/types/common'

/** B-05 菜品：分页 + 筛选。 */
export function listDishes(params: DishListParams): Promise<DishAdminVO[]> {
  return getPage<DishAdminVO>('/admin/dishes', params)
}

export function getDish(id: number): Promise<DishAdminVO> {
  return get<DishAdminVO>(`/admin/dishes/${id}`)
}

export function createDish(req: DishSaveReq): Promise<DishAdminVO> {
  return post<DishAdminVO>('/admin/dishes', req)
}

export function updateDish(id: number, req: DishSaveReq): Promise<DishAdminVO> {
  return put<DishAdminVO>(`/admin/dishes/${id}`, req)
}

export function toggleDishStatus(id: number): Promise<DishAdminVO> {
  return put<DishAdminVO>(`/admin/dishes/${id}/status`)
}

export function deleteDish(id: number): Promise<null> {
  return del<null>(`/admin/dishes/${id}`)
}
