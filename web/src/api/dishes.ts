import { getAdminPage, get, post, put, del } from './http'
import type {
  AdminPage,
  DishAdminListItemVO,
  DishAdminVO,
  DishSaveReq,
  DishListParams,
  OnOffStatus,
} from '@/types/common'

/** A3 菜品：列表（管理端分页壳 `AdminPageResult` = `records` + `total`；**瘦身列表行 VO**） */
export function listDishes(params: DishListParams): Promise<AdminPage<DishAdminListItemVO>> {
  return getAdminPage<DishAdminListItemVO>('/admin/dishes', params)
}

/** A3 菜品详情（编辑回填，全字段） */
export function getDish(id: number): Promise<DishAdminVO> {
  return get<DishAdminVO>(`/admin/dishes/${id}`)
}

/** A3 新增（返回新建的 VO） */
export function createDish(req: DishSaveReq): Promise<DishAdminVO> {
  return post<DishAdminVO>('/admin/dishes', req)
}

/** A3 修改（**可编辑字段整体替换**） */
export function updateDish(id: number, req: DishSaveReq): Promise<null> {
  return put<null>(`/admin/dishes/${id}`, req)
}

/** A3 复制为新菜品（只传新菜名，其余字段复制源菜品；**副本默认下架**） */
export function copyDish(id: number, name: string): Promise<DishAdminVO> {
  return post<DishAdminVO>(`/admin/dishes/${id}/copy`, { name })
}

/** A3 上下架（只改 `status`） */
export function updateDishStatus(id: number, status: OnOffStatus): Promise<null> {
  return put<null>(`/admin/dishes/${id}/status`, { status })
}

/** A3 删除（评价级联删除） */
export function deleteDish(id: number): Promise<null> {
  return del<null>(`/admin/dishes/${id}`)
}
